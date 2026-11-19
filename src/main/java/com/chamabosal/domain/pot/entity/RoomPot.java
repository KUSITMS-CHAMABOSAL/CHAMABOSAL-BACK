package com.chamabosal.domain.pot.entity;

import com.chamabosal.domain.room.entity.Room;
import com.chamabosal.global.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 공양미 한 솥 (POT-01). 목표를 채운 뒤에도 연장 전까지는 이 솥에 계속 쌓인다 (Q-86). */
@Getter
@Entity
@Table(name = "room_pots", uniqueConstraints = {
        @UniqueConstraint(name = "uk_room_pots_room_seq", columnNames = {"room_id", "seq"})
})
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RoomPot extends BaseEntity {

    private static final int REWARD_PERCENT = 10;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "room_id", nullable = false)
    private Room room;

    @Column(nullable = false)
    private int seq;

    @Column(nullable = false)
    private int goalAmount;

    @Column(nullable = false)
    private int amount;

    @Column(nullable = false)
    private int carriedOver;

    /** NULL이면 진행 중인 솥 */
    private LocalDateTime completedAt;

    /** 첫 솥은 그 방에 처음 적립할 때 방 목표 석으로 만든다. */
    public static RoomPot first(Room room) {
        RoomPot pot = new RoomPot();
        pot.room = room;
        pot.seq = 1;
        pot.goalAmount = room.getPotGoalAmount();
        return pot;
    }

    public void add(int delta) {
        amount += delta;
    }

    /** 0 아래로 내려가지 않게 뺀다 (POT-01 11번). 실제로 뺀 석. */
    public int subtract(int delta) {
        int subtracted = Math.min(delta, amount);
        amount -= subtracted;
        return subtracted;
    }

    public boolean isFilled() {
        return amount >= goalAmount;
    }

    public boolean isCompleted() {
        return completedAt != null;
    }

    public void complete(LocalDateTime now) {
        completedAt = now;
    }

    /** 목표의 10%를 10석 단위로 반올림해 준다. 350석이면 40석이다 (RICE-01 8번, Q-81). */
    public int getRewardAmount() {
        return (goalAmount * REWARD_PERCENT + 500) / 1000 * 10;
    }
}
