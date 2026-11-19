package com.chamabosal.domain.tap.service;

import com.chamabosal.domain.rice.entity.RiceReason;
import com.chamabosal.domain.rice.entity.RiceRefType;
import com.chamabosal.domain.rice.repository.RiceHistoryRepository;
import com.chamabosal.domain.rice.service.RiceService;
import com.chamabosal.domain.room.entity.Room;
import com.chamabosal.domain.room.entity.RoomMember;
import com.chamabosal.domain.room.repository.RoomMemberRepository;
import com.chamabosal.domain.room.repository.RoomRepository;
import com.chamabosal.domain.round.entity.Round;
import com.chamabosal.domain.round.entity.RoundResultType;
import com.chamabosal.domain.round.entity.RoundStatus;
import com.chamabosal.domain.round.repository.RoundRepository;
import com.chamabosal.domain.tap.dto.response.MoktakCompleteResponse;
import com.chamabosal.domain.tap.repository.TapRepository;
import com.chamabosal.global.common.exception.BaseErrorCode;
import com.chamabosal.global.common.exception.BaseException;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 목탁 수행 완료 (RESULT-03 9번, RICE-01 6·7번). 지목된 사람이 받은 톡톡만큼 끝까지 두드리면 화면이 두드린 횟수와 함께 한 번 보내고,
 * 번뇌 완료 보상 +10석(개인만)을 준다. 두드린 횟수가 받은 톡톡 합계보다 적으면 주지 않는다.
 * 방어와 겹치지 않게 방 행 락 안에서 결과가 아직 `PICKED`인지 확인한다.
 */
@Service
@RequiredArgsConstructor
public class MoktakService {

    public static final int BEONNOE_REWARD = 10;

    private final RoomRepository roomRepository;
    private final RoomMemberRepository roomMemberRepository;
    private final RoundRepository roundRepository;
    private final TapRepository tapRepository;
    private final RiceHistoryRepository riceHistoryRepository;
    private final RiceService riceService;
    private final Clock clock;

    /** 확인 순서: 없는 회차 → 방 멤버 아님 → 지목된 사람 아님 → 보상 없는 결과 → 이미 완료(성공) → 전달 전 → 받은 톡톡 없음 → 기한 지남 → 덜 두드림. */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public MoktakCompleteResponse complete(Long userId, Long roundId, long count) {
        Long roomId = roundRepository.findRoomIdByRoundId(roundId)
                .orElseThrow(() -> new BaseException(BaseErrorCode.ROUND_NOT_FOUND));
        Room room = roomRepository.findWithLockById(roomId).orElseThrow();
        RoomMember me = roomMemberRepository.findByRoom_IdAndUser_IdAndLeftAtIsNull(roomId, userId)
                .orElseThrow(() -> new BaseException(BaseErrorCode.NOT_ROOM_MEMBER));
        Round round = roundRepository.findById(roundId)
                .orElseThrow(() -> new BaseException(BaseErrorCode.ROUND_NOT_FOUND));
        if (round.getPickedMember() == null || !round.getPickedMember().getId().equals(me.getId())) {
            throw new BaseException(BaseErrorCode.NOT_PICKED_MEMBER);
        }
        if (round.getResultType() != RoundResultType.PICKED) {
            throw new BaseException(BaseErrorCode.MOKTAK_NOT_AVAILABLE);
        }
        if (isDone(userId, roundId)) {
            return new MoktakCompleteResponse(0);
        }
        if (round.isTapOpen(LocalDateTime.now(clock))) {
            throw new BaseException(BaseErrorCode.MOKTAK_NOT_DELIVERED);
        }
        if (!tapRepository.existsByRound_IdAndCountGreaterThan(roundId, 0)) {
            throw new BaseException(BaseErrorCode.MOKTAK_NO_TAPS);
        }
        if (isExpired(round)) {
            throw new BaseException(BaseErrorCode.MOKTAK_EXPIRED);
        }
        // 전달 뒤에는 톡톡이 더 오지 않아 합계가 바뀌지 않는다. 더 두드린 것은 막지 않는다.
        if (count < tapRepository.sumCountByRoundId(roundId)) {
            throw new BaseException(BaseErrorCode.MOKTAK_NOT_FINISHED);
        }
        return new MoktakCompleteResponse(riceService.accrue(
                room, me, RiceReason.BEONNOE, RiceRefType.ROUND, roundId, BEONNOE_REWARD));
    }

    /**
     * 지목된 본인이 지금 목탁 수행을 해야 하는지. 완료 요청이 보상을 주는 조건과 같다 (투표 탭 MOKTAK, VOTE-05).
     * `PICKED`(방어 안 함), 톡톡 전달됨, 받은 톡톡 1번 이상, 아직 안 마침, 다음 투표 전, 방 인원 2명 이상.
     * 완료 여부는 보상 기록으로 보는데 1명 방은 보상이 쌓이지 않아, 인원 조건이 없으면 마쳐도 계속 남는다 (코드리뷰 반영).
     */
    @Transactional(readOnly = true)
    public boolean isMoktakDue(Long userId, Round round, RoomMember me, LocalDateTime now) {
        return round.getResultType() == RoundResultType.PICKED
                && round.getPickedMember() != null && round.getPickedMember().getId().equals(me.getId())
                && !round.isTapOpen(now)
                && tapRepository.existsByRound_IdAndCountGreaterThan(round.getId(), 0)
                && !isDone(userId, round.getId())
                && !isExpired(round)
                && riceService.isAccruing(round.getRoom().getId());
    }

    /** 목탁 수행에서 두드려야 하는 횟수: 그 회차에 받은 톡톡 합계. */
    @Transactional(readOnly = true)
    public long getMoktakCount(Long roundId) {
        return tapRepository.sumCountByRoundId(roundId);
    }

    private boolean isDone(Long userId, Long roundId) {
        return riceHistoryRepository.existsByUser_IdAndReasonAndRefTypeAndRefId(
                userId, RiceReason.BEONNOE, RiceRefType.ROUND, roundId);
    }

    /** 다음 투표가 열렸으면 목탁 수행 기한이 지났다 (Q-92). */
    private boolean isExpired(Round round) {
        return roundRepository.existsByRoom_IdAndRoundDateAfterAndStatusIn(round.getRoom().getId(),
                round.getRoundDate(), List.of(RoundStatus.VOTING, RoundStatus.ANNOUNCED));
    }
}
