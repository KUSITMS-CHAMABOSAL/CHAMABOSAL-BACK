package com.chamabosal.domain.tap.service;

import com.chamabosal.domain.pot.entity.PotReason;
import com.chamabosal.domain.pot.service.PotService;
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
import com.chamabosal.domain.tap.dto.response.ReceivedTapsResponse;
import com.chamabosal.domain.tap.dto.response.TapSendResponse;
import com.chamabosal.domain.tap.entity.Tap;
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

/** 이마 톡톡 (RESULT-03). 지목된 사람에게 회차당 한 세트, 투표 마감 후 5시간 동안 보내고 그 뒤 한 번에 전달한다. */
@Service
@RequiredArgsConstructor
public class TapService {

    public static final int TAP_REWARD = 5;

    private final RoomRepository roomRepository;
    private final RoomMemberRepository roomMemberRepository;
    private final RoundRepository roundRepository;
    private final TapRepository tapRepository;
    private final RiceService riceService;
    private final RiceHistoryRepository riceHistoryRepository;
    private final PotService potService;
    private final Clock clock;

    /**
     * 결과(`PICKED`·`DEFENDED`)를 방어와 같은 방 행 락 안에서 확인한다. roundId만 받아서 방 ID만 먼저 읽고
     * 락을 잡은 뒤 READ COMMITTED로 회차를 읽는다 (VoteSubmitService와 같은 이유). 같은 사람의 중복 전송은 UNIQUE도 막는다.
     */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public TapSendResponse send(Long userId, Long roundId, int count) {
        Long roomId = roundRepository.findRoomIdByRoundId(roundId)
                .orElseThrow(() -> new BaseException(BaseErrorCode.ROUND_NOT_FOUND));
        Room room = roomRepository.findWithLockById(roomId).orElseThrow();
        RoomMember me = roomMemberRepository.findByRoom_IdAndUser_IdAndLeftAtIsNull(roomId, userId)
                .orElseThrow(() -> new BaseException(BaseErrorCode.NOT_ROOM_MEMBER));
        Round round = roundRepository.findById(roundId)
                .orElseThrow(() -> new BaseException(BaseErrorCode.ROUND_NOT_FOUND));

        if (round.getStatus() != RoundStatus.ANNOUNCED || round.getResultType() != RoundResultType.PICKED) {
            throw new BaseException(BaseErrorCode.TAP_NOT_AVAILABLE);
        }
        if (round.getPickedMember().getId().equals(me.getId())) {
            throw new BaseException(BaseErrorCode.TAP_SELF);
        }
        if (tapRepository.existsByRound_IdAndFromMember_Id(roundId, me.getId())) {
            throw new BaseException(BaseErrorCode.ALREADY_TAPPED);
        }
        if (!round.isTapOpen(LocalDateTime.now(clock))) {
            throw new BaseException(BaseErrorCode.TAP_EXPIRED);
        }

        Tap tap = tapRepository.save(Tap.create(round, me, count));
        int riceEarned = count > 0
                ? riceService.accrue(room, me, RiceReason.TAP, RiceRefType.TAP, tap.getId(), TAP_REWARD)
                : 0;
        int potEarned = potService.accrue(room, me, PotReason.TAP, tap.getId(), riceEarned);
        return new TapSendResponse(riceEarned, potEarned);
    }

    @Transactional(readOnly = true)
    public ReceivedTapsResponse getReceived(Long userId, Long roundId) {
        Round round = findMinePicked(userId, roundId);
        boolean isDelivered = !round.isTapOpen(LocalDateTime.now(clock));
        // 전달 전이거나 방어돼 무효가 된 톡톡은 보여주지 않는다 (RESULT-03 6·7번).
        List<Tap> taps = isDelivered && round.getResultType() == RoundResultType.PICKED
                ? tapRepository.findReceivedWithSender(roundId) : List.of();
        boolean isMoktakDone = riceHistoryRepository.existsByUser_IdAndReasonAndRefTypeAndRefId(
                userId, RiceReason.BEONNOE, RiceRefType.ROUND, roundId);
        return ReceivedTapsResponse.of(isDelivered, round.getTapClosesAt(), isMoktakDone, taps);
    }

    /** 그때까지 받은 톡톡을 모두 재생됨으로 바꾼다. 재시도해도 성공이다. */
    @Transactional
    public void markPlayed(Long userId, Long roundId) {
        findMinePicked(userId, roundId);
        tapRepository.markPlayed(roundId, LocalDateTime.now(clock));
    }

    /** 받은 톡톡은 그 회차에 지목된 본인만 볼 수 있다 (TAP_005). */
    private Round findMinePicked(Long userId, Long roundId) {
        Round round = roundRepository.findById(roundId)
                .orElseThrow(() -> new BaseException(BaseErrorCode.ROUND_NOT_FOUND));
        RoomMember me = roomMemberRepository.findByRoom_IdAndUser_IdAndLeftAtIsNull(round.getRoom().getId(), userId)
                .orElseThrow(() -> new BaseException(BaseErrorCode.NOT_ROOM_MEMBER));
        if (round.getPickedMember() == null || !round.getPickedMember().getId().equals(me.getId())) {
            throw new BaseException(BaseErrorCode.NOT_PICKED_MEMBER);
        }
        return round;
    }
}
