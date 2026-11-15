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

    /** 확인 순서: 없는 회차 → 방 멤버 아님 → 지목된 사람 아님 → 보상 없는 결과 → 이미 완료(성공) → 받은 톡톡 없음 → 기한 지남 → 덜 두드림. */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public MoktakCompleteResponse complete(Long userId, Long roundId, int count) {
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
        if (riceHistoryRepository.existsByUser_IdAndReasonAndRefTypeAndRefId(
                userId, RiceReason.BEONNOE, RiceRefType.ROUND, roundId)) {
            return new MoktakCompleteResponse(0);
        }
        if (!tapRepository.existsByRound_IdAndCountGreaterThan(roundId, 0)) {
            throw new BaseException(BaseErrorCode.MOKTAK_NO_TAPS);
        }
        if (roundRepository.existsByRoom_IdAndRoundDateAfterAndStatusIn(
                roomId, round.getRoundDate(), List.of(RoundStatus.VOTING, RoundStatus.ANNOUNCED))) {
            throw new BaseException(BaseErrorCode.MOKTAK_EXPIRED);
        }
        // 수행 중에 톡톡이 더 오면 합계가 늘어나므로 "같음"이 아니라 "이상"으로 본다.
        if (count < tapRepository.sumCountByRoundId(roundId)) {
            throw new BaseException(BaseErrorCode.MOKTAK_NOT_FINISHED);
        }
        return new MoktakCompleteResponse(riceService.accrue(
                room, me, RiceReason.BEONNOE, RiceRefType.ROUND, roundId, BEONNOE_REWARD));
    }
}
