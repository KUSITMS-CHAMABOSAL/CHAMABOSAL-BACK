package com.chamabosal.domain.defense.service;

import com.chamabosal.domain.defense.dto.response.DefenseTicketPurchaseResponse;
import com.chamabosal.domain.defense.entity.DefenseTicket;
import com.chamabosal.domain.defense.repository.DefenseTicketRepository;
import com.chamabosal.domain.rice.entity.RiceRefType;
import com.chamabosal.domain.rice.service.RiceService;
import com.chamabosal.domain.room.entity.RoomMember;
import com.chamabosal.domain.room.repository.RoomMemberRepository;
import com.chamabosal.domain.room.repository.RoomRepository;
import com.chamabosal.domain.round.entity.Round;
import com.chamabosal.domain.round.entity.RoundResultType;
import com.chamabosal.domain.round.entity.RoundStatus;
import com.chamabosal.domain.round.repository.RoundRepository;
import com.chamabosal.domain.user.entity.User;
import com.chamabosal.domain.user.repository.UserRepository;
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
 * 방어권 구매·사용 (SHIELD-01·02). 둘 다 사용자 전체 잔액·보유 방어권을 다뤄 사용자 행 락을 먼저 잡는다.
 * 사용은 결과를 바꾸고 톡톡과 겹치지 않게 방 행 락도 잡는다. 순서는 방 만들기·입장·나가기와 같은 사용자 → 방이다.
 * 락을 잡은 뒤의 조회가 커밋된 값을 보도록 READ COMMITTED로 읽는다.
 */
@Service
@RequiredArgsConstructor
public class DefenseService {

    public static final int PRICE = 100;
    public static final int DEFENSE_MINUTES = 2;

    private final UserRepository userRepository;
    private final RoomRepository roomRepository;
    private final RoomMemberRepository roomMemberRepository;
    private final RoundRepository roundRepository;
    private final DefenseTicketRepository defenseTicketRepository;
    private final RiceService riceService;
    private final Clock clock;

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public DefenseTicketPurchaseResponse purchase(Long userId) {
        User user = lockUser(userId);
        DefenseTicket ticket = defenseTicketRepository.save(DefenseTicket.purchase(user, LocalDateTime.now(clock)));
        riceService.spend(user, PRICE, RiceRefType.DEFENSE, ticket.getId());
        return new DefenseTicketPurchaseResponse(riceService.getDisplayBalance(userId));
    }

    /**
     * 확인 순서: 없는 회차 → 방 멤버 아님 → 발표 전 → 이미 방어함(성공) → 지목된 사람 아님 → 방어 시간 지남 → 방어권 없음
     * (SHIELD-02 10번). 결과를 보지 않고 바로 쓰면 이 시각을 처음 조회 시각으로 남긴다 (3번).
     */
    @Transactional(isolation = Isolation.READ_COMMITTED)
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

    /** 처음 조회 후 2분이 지났거나, 결과를 열지 않은 채 다음 투표가 열렸으면 방어할 수 없다 (SHIELD-02 3번, Q-77 임시 결정). */
    public boolean isExpired(Round round, LocalDateTime now) {
        return now.isAfter(round.getPickedSeenAt().plusMinutes(DEFENSE_MINUTES))
                || roundRepository.existsByRoom_IdAndRoundDateAfterAndStatusIn(round.getRoom().getId(),
                round.getRoundDate(), List.of(RoundStatus.VOTING, RoundStatus.ANNOUNCED));
    }

    private User lockUser(Long userId) {
        return userRepository.findWithLockById(userId)
                .filter(found -> !found.isDeleted())
                .orElseThrow(() -> new BaseException(BaseErrorCode.UNAUTHENTICATED));
    }
}
