package com.chamabosal.domain.tap.service;

import com.chamabosal.domain.demo.service.DemoPlayService;
import com.chamabosal.domain.pot.entity.PotReason;
import com.chamabosal.domain.pot.service.PotService;
import com.chamabosal.domain.rice.entity.RiceReason;
import com.chamabosal.domain.rice.entity.RiceRefType;
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
import com.chamabosal.domain.tap.entity.TapStart;
import com.chamabosal.domain.tap.repository.TapRepository;
import com.chamabosal.domain.tap.repository.TapStartRepository;
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
    private final TapStartRepository tapStartRepository;
    private final MoktakService moktakService;
    private final RiceService riceService;
    private final PotService potService;
    private final DemoPlayService demoPlayService;
    private final Clock clock;

    /**
     * 톡톡 화면에 들어갈 때 시작을 남긴다 (RESULT-03 10번). 방어와 같은 방 행 락 안에서 `PICKED`인지 확인해서,
     * 시작 기록이 있으면 방어 전에 시작한 것이다. 이미 시작했으면 다시 확인하지 않고 성공한다. 새로 남겼으면 true.
     */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public boolean start(Long userId, Long roundId) {
        Long roomId = roundRepository.findRoomIdByRoundId(roundId)
                .orElseThrow(() -> new BaseException(BaseErrorCode.ROUND_NOT_FOUND));
        roomRepository.findWithLockById(roomId).orElseThrow();
        RoomMember me = roomMemberRepository.findByRoom_IdAndUser_IdAndLeftAtIsNull(roomId, userId)
                .orElseThrow(() -> new BaseException(BaseErrorCode.NOT_ROOM_MEMBER));
        Round round = roundRepository.findById(roundId)
                .orElseThrow(() -> new BaseException(BaseErrorCode.ROUND_NOT_FOUND));
        if (tapStartRepository.existsByRound_IdAndMember_Id(roundId, me.getId())) {
            return false;
        }
        checkCanTap(round, me, false);
        tapStartRepository.save(TapStart.create(round, me));
        return true;
    }

    /**
     * 결과(`PICKED`·`DEFENDED`)를 방어와 같은 방 행 락 안에서 확인한다. roundId만 받아서 방 ID만 먼저 읽고
     * 락을 잡은 뒤 READ COMMITTED로 회차를 읽는다 (VoteSubmitService와 같은 이유). 같은 사람의 중복 전송은 UNIQUE도 막는다.
     * 방어된 회차는 방어 전에 시작한 사람만 보낼 수 있고, 톡톡은 전달하지 않지만 +5석은 준다 (RESULT-03 7·10번).
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
        checkCanTap(round, me, tapStartRepository.existsByRound_IdAndMember_Id(roundId, me.getId()));

        Tap tap = tapRepository.save(Tap.create(round, me, count));
        int riceEarned = count > 0
                ? riceService.accrue(room, me, RiceReason.TAP, RiceRefType.TAP, tap.getId(), TAP_REWARD)
                : 0;
        int potEarned = potService.accrue(room, me, PotReason.TAP, tap.getId(), riceEarned);
        demoPlayService.onTapSent(round, me);
        return new TapSendResponse(riceEarned, potEarned);
    }

    /**
     * 톡톡 시작·보내기 공통 확인. 막히면 그 사유를 던진다 (findTapBlock).
     */
    private void checkCanTap(Round round, RoomMember me, boolean isStartedBeforeDefense) {
        BaseErrorCode block = findTapBlock(round, me, isStartedBeforeDefense);
        if (block != null) {
            throw new BaseException(block);
        }
    }

    /**
     * 지금 톡톡을 보낼 수 있으면 null, 아니면 막히는 사유. 결과 화면 canTap과 투표 탭 TAP 상태도 이 판단을 쓴다 (RESULT-03, VOTE-05).
     * 확인 순서: 톡톡 없는 결과 → 지목된 사람이 나감 → 본인 → 회차 멤버 아님 → 이미 보냄 → 톡톡 마감.
     * isStartedBeforeDefense가 참이면 방어된(`DEFENDED`) 결과도 허용한다.
     */
    @Transactional(readOnly = true)
    public BaseErrorCode findTapBlock(Round round, RoomMember me, boolean isStartedBeforeDefense) {
        boolean isPicked = round.getResultType() == RoundResultType.PICKED;
        boolean isDefendedAfterStart = round.getResultType() == RoundResultType.DEFENDED && isStartedBeforeDefense;
        if (round.getStatus() != RoundStatus.ANNOUNCED || !(isPicked || isDefendedAfterStart)) {
            return BaseErrorCode.TAP_NOT_AVAILABLE;
        }
        // 지목된 사람이 방을 나갔으면 이마 톡톡이 없다 (RESULT-03 예외, Q-27).
        if (!round.getPickedMember().isCurrent()) {
            return BaseErrorCode.TAP_NOT_AVAILABLE;
        }
        if (round.getPickedMember().getId().equals(me.getId())) {
            return BaseErrorCode.TAP_SELF;
        }
        // 투표·인증과 상관없이 그 회차 멤버면 보낸다 (RESULT-02 4번). 투표 시작 뒤 들어왔거나 나갔다가 다시 들어온 멤버는
        // 투표 제출·마감(Q-36)처럼 그 회차 명단 밖으로 보고 막는다.
        if (round.isJoinedAfterVoteStart(me)) {
            return BaseErrorCode.TAP_NOT_ROUND_MEMBER;
        }
        if (tapRepository.existsByRound_IdAndFromMember_Id(round.getId(), me.getId())) {
            return BaseErrorCode.ALREADY_TAPPED;
        }
        if (!round.isTapOpen(LocalDateTime.now(clock))) {
            return BaseErrorCode.TAP_EXPIRED;
        }
        return null;
    }

    @Transactional(readOnly = true)
    public ReceivedTapsResponse getReceived(Long userId, Long roundId) {
        Round round = findMinePicked(userId, roundId);
        boolean isDelivered = !round.isTapOpen(LocalDateTime.now(clock));
        // 전달 전이거나 방어돼 무효가 된 톡톡은 보여주지 않는다 (RESULT-03 6·7번).
        List<Tap> taps = isDelivered && round.getResultType() == RoundResultType.PICKED
                ? tapRepository.findReceivedWithSender(roundId) : List.of();
        boolean isMoktakDone = moktakService.isEnded(userId, round);
        return ReceivedTapsResponse.of(isDelivered, round.getTapClosesAt(), isMoktakDone, taps);
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
