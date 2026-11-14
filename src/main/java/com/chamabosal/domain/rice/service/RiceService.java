package com.chamabosal.domain.rice.service;

import com.chamabosal.domain.rice.entity.RiceHistory;
import com.chamabosal.domain.rice.entity.RiceReason;
import com.chamabosal.domain.rice.entity.RiceRefType;
import com.chamabosal.domain.rice.repository.RiceHistoryRepository;
import com.chamabosal.domain.room.entity.Room;
import com.chamabosal.domain.room.entity.RoomMember;
import com.chamabosal.domain.room.repository.RoomMemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 개인 공양미 적립·회수·나가기 차감 (RICE-01, RICE-02, RICE-03). 방 행 락을 잡은 트랜잭션 안에서 부른다.
 * 같은 사유·원인으로는 한 번만 기록하고, 동시에 들어오면 UNIQUE 제약이 막는다.
 */
@Service
@RequiredArgsConstructor
public class RiceService {

    public static final int CERTIFICATION_REWARD = 10;
    public static final int VOTE_REWARD = 5;
    private static final int MIN_MEMBERS = 2;

    private final RiceHistoryRepository riceHistoryRepository;
    private final RoomMemberRepository roomMemberRepository;

    /** 방 인원이 2명 이상일 때만 적립한다 (RICE-01 1번). 적립한 석, 적립하지 않았으면 0. */
    @Transactional(propagation = Propagation.MANDATORY)
    public int accrue(Room room, RoomMember member, RiceReason reason, RiceRefType refType, Long refId, int amount) {
        if (roomMemberRepository.countByRoom_IdAndLeftAtIsNull(room.getId()) < MIN_MEMBERS) {
            return 0;
        }
        Long userId = member.getUser().getId();
        if (riceHistoryRepository.existsByUser_IdAndReasonAndRefTypeAndRefId(userId, reason, refType, refId)) {
            return 0;
        }
        riceHistoryRepository.save(RiceHistory.create(member.getUser(), room, amount, reason, refType, refId));
        return amount;
    }

    /** 그 인증으로 지급한 만큼 회수한다. 지급 기록이 없거나 이미 회수했으면 0 (RICE-02 1·6번). */
    @Transactional(propagation = Propagation.MANDATORY)
    public int recallCertification(RoomMember member, Long certificationId) {
        Long userId = member.getUser().getId();
        return riceHistoryRepository.findByUser_IdAndReasonAndRefTypeAndRefId(
                        userId, RiceReason.CERTIFICATION, RiceRefType.CERTIFICATION, certificationId)
                .filter(paid -> !riceHistoryRepository.existsByUser_IdAndReasonAndRefTypeAndRefId(
                        userId, RiceReason.RECALL, RiceRefType.CERTIFICATION, certificationId))
                .map(paid -> {
                    riceHistoryRepository.save(RiceHistory.create(paid.getUser(), paid.getRoom(), -paid.getDelta(),
                            RiceReason.RECALL, RiceRefType.CERTIFICATION, certificationId));
                    return paid.getDelta();
                })
                .orElse(0);
    }

    /**
     * 목표를 채우기 전에 나가면 그 방에서 모은 공양미를 뺀다 (RICE-03). 잔액이 0석 아래로 내려가지 않게 줄이고,
     * 이미 0석 이하면 빼지 않는다 (Q-88). 다시 들어오면 같은 멤버 행이라 원인 없이 기록한다 (RICE-03 7번).
     * 한 솥(POT-01)이 아직 없어 모든 방이 목표를 채우기 전이다. 뺀 석, 빼지 않았으면 0.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public int deductOnLeave(Room room, RoomMember member) {
        Long userId = member.getUser().getId();
        long collected = riceHistoryRepository.sumRoomDeltaSinceLastLeave(userId, room.getId(), RiceReason.LEAVE);
        long balance = riceHistoryRepository.sumDeltaByUserId(userId);
        int amount = (int) Math.min(collected, Math.max(0, balance));
        if (amount <= 0) {
            return 0;
        }
        riceHistoryRepository.save(RiceHistory.create(member.getUser(), room, -amount, RiceReason.LEAVE, null, null));
        return amount;
    }

    /** 화면 표시 잔액. 실제 잔액이 음수면 0석이다 (RICE-01-6). */
    @Transactional(readOnly = true)
    public int getDisplayBalance(Long userId) {
        return (int) Math.max(0, riceHistoryRepository.sumDeltaByUserId(userId));
    }
}
