package com.chamabosal.domain.defense.service;

import com.chamabosal.domain.defense.dto.response.DefenseTicketPurchaseResponse;
import com.chamabosal.domain.defense.entity.DefenseTicket;
import com.chamabosal.domain.defense.repository.DefenseTicketRepository;
import com.chamabosal.domain.rice.entity.RiceReason;
import com.chamabosal.domain.rice.entity.RiceRefType;
import com.chamabosal.domain.rice.repository.RiceHistoryRepository;
import com.chamabosal.domain.rice.service.RiceService;
import com.chamabosal.domain.room.entity.RoomMember;
import com.chamabosal.domain.room.repository.RoomMemberRepository;
import com.chamabosal.domain.room.repository.RoomRepository;
import com.chamabosal.domain.round.entity.Round;
import com.chamabosal.domain.round.entity.RoundResultType;
import com.chamabosal.domain.round.entity.RoundStatus;
import com.chamabosal.domain.round.entity.RoundParticipant;
import com.chamabosal.domain.round.repository.RoundParticipantRepository;
import com.chamabosal.domain.round.repository.RoundRepository;
import com.chamabosal.domain.user.entity.User;
import com.chamabosal.domain.user.repository.UserRepository;
import com.chamabosal.global.common.exception.BaseErrorCode;
import com.chamabosal.global.common.exception.BaseException;
import java.time.Clock;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 방어권 구매·사용 (SHIELD-01·02). 둘 다 사용자 전체 잔액·보유 방어권을 다뤄 사용자 행 락을 먼저 잡는다.
 * 사용은 결과를 바꾸고 톡톡과 겹치지 않게 방 행 락도 잡는다. 순서는 방 만들기·입장·나가기와 같은 사용자 → 방이다.
 * 락을 잡은 뒤의 조회가 커밋된 값을 보도록 READ COMMITTED로 읽는다.
 */
@Service
@RequiredArgsConstructor
public class DefenseService {

    public static final int PRICE = 100;

    private final UserRepository userRepository;
    private final RoomRepository roomRepository;
    private final RoomMemberRepository roomMemberRepository;
    private final RoundRepository roundRepository;
    private final RoundParticipantRepository roundParticipantRepository;
    private final DefenseTicketRepository defenseTicketRepository;
    private final RiceService riceService;
    private final RiceHistoryRepository riceHistoryRepository;
    private final Clock clock;

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public DefenseTicketPurchaseResponse purchase(Long userId) {
        User user = lockUser(userId);
        DefenseTicket ticket = defenseTicketRepository.save(DefenseTicket.purchase(user, LocalDateTime.now(clock)));
        riceService.spend(user, PRICE, RiceRefType.DEFENSE, ticket.getId());
        return new DefenseTicketPurchaseResponse(riceService.getDisplayBalance(userId));
    }

    /**
     * 확인 순서: 없는 회차 → 방 멤버 아님 → 발표 전 → 이미 방어함(성공) → 지목된 사람 아님 → 인증하지 않음 → 목탁 수행 마침
     * → 방어 시간 지남 → 방어권 없음 (SHIELD-02 10번). 결과를 보지 않고 바로 쓰면 이 시각을 처음 연 시각으로 남긴다 (3번).
     * 처음 연 시각을 남긴 뒤 거부돼도 그 시각은 커밋한다. 롤백되면 나중 요청에서 2분이 새로 시작된다.
     */
    @Transactional(isolation = Isolation.READ_COMMITTED, noRollbackFor = BaseException.class)
    public void use(Long userId, Long roundId) {
        Long roomId = roundRepository.findRoomIdByRoundId(roundId)
                .orElseThrow(() -> new BaseException(BaseErrorCode.ROUND_NOT_FOUND));
        lockUser(userId);
        roomRepository.findWithLockById(roomId);
        RoomMember me = roomMemberRepository.findByRoom_IdAndUser_IdAndLeftAtIsNull(roomId, userId)
                .orElseThrow(() -> new BaseException(BaseErrorCode.NOT_ROOM_MEMBER));
        Round round = roundRepository.findById(roundId)
                .orElseThrow(() -> new BaseException(BaseErrorCode.ROUND_NOT_FOUND));
        if (round.getStatus() != RoundStatus.ANNOUNCED) {
            throw new BaseException(BaseErrorCode.RESULT_NOT_ANNOUNCED);
        }
        boolean isMePicked = round.getPickedMember() != null && round.getPickedMember().getId().equals(me.getId());
        if (isMePicked && round.getResultType() == RoundResultType.DEFENDED) {
            return;
        }
        if (!isMePicked || round.getResultType() != RoundResultType.PICKED) {
            throw new BaseException(BaseErrorCode.NOT_PICKED_FOR_DEFENSE);
        }
        if (!hasCertified(round)) {
            throw new BaseException(BaseErrorCode.DEFENSE_NOT_CERTIFIED);
        }
        // 목탁 수행과 같은 방 행 락 안이라 보상 기록과 방어가 엇갈리지 않는다 (SHIELD-02 12번).
        if (isMoktakDone(userId, roundId)) {
            throw new BaseException(BaseErrorCode.DEFENSE_AFTER_MOKTAK);
        }

        LocalDateTime now = LocalDateTime.now(clock);
        round.markPickedSeenIfAbsent(now);
        if (isExpired(round, now)) {
            throw new BaseException(BaseErrorCode.DEFENSE_EXPIRED);
        }
        DefenseTicket ticket = defenseTicketRepository.findFirstByUser_IdAndUsedAtIsNullOrderByPurchasedAtAscIdAsc(userId)
                .orElseThrow(() -> new BaseException(BaseErrorCode.NO_DEFENSE_TICKET));
        ticket.use(round, now);
        round.defend();
    }

    /** 처음 연 지 2분이 지났거나, 톡톡 마감 뒤에 처음 열었으면 방어할 수 없다 (SHIELD-02 3번, Q-75·Q-77). */
    public boolean isExpired(Round round, LocalDateTime now) {
        LocalDateTime closesAt = round.getDefenseClosesAt();
        return closesAt == null || now.isAfter(closesAt);
    }

    /** 지목된 사람이 그 회차에 인증했는지. 투표 시작 시점의 투표권으로 본다 (SHIELD-02 8번, Q-76). */
    public boolean hasCertified(Round round) {
        return roundParticipantRepository.findByRound_IdAndMember_Id(round.getId(), round.getPickedMember().getId())
                .map(RoundParticipant::isCanVote)
                .orElse(false);
    }

    /** 그 회차 목탁 수행을 마쳐 번뇌 완료 보상을 받았으면 방어할 수 없다 (SHIELD-02 12번). 결과 화면도 이 값으로 사용 가능 여부를 정한다. */
    public boolean isMoktakDone(Long userId, Long roundId) {
        return riceHistoryRepository.existsByUser_IdAndReasonAndRefTypeAndRefId(
                userId, RiceReason.BEONNOE, RiceRefType.ROUND, roundId);
    }

    private User lockUser(Long userId) {
        return userRepository.findWithLockById(userId)
                .filter(found -> !found.isDeleted())
                .orElseThrow(() -> new BaseException(BaseErrorCode.UNAUTHENTICATED));
    }
}
