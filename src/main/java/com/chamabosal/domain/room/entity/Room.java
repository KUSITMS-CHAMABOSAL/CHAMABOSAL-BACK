package com.chamabosal.domain.room.entity;

import com.chamabosal.global.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Getter
@Entity
@Table(name = "rooms")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Room extends BaseEntity {

    /** 함께할 인원(memberLimit)과 관계없는 입장 제한. 함께할 인원은 예상 기간 계산에만 쓴다 (INVITE-02 5번). */
    public static final int MAX_MEMBERS = 4;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 12)
    private String name;

    /** MySQL ENUM이나 값 목록 CHECK 없이 varchar로 만든다. 값을 추가해도 ddl-auto update가 막지 않는다. */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, columnDefinition = "varchar(20)")
    private RoomGoal goal;

    /** 함께할 인원 2~4명. 예상 기간 계산에만 쓰고 입장 제한은 MAX_MEMBERS다. */
    @Column(nullable = false)
    private int memberLimit;

    /** 공양미 한 솥 목표 석 (ROOM-01 13번). 솥을 만들 때 복사한다. */
    @Column(nullable = false)
    private int potGoalAmount;

    /** 방을 먼저 저장해야 멤버를 만들 수 있어 NULL을 허용한다. 같은 요청 안에서 채운다. */
    private Long ownerMemberId;

    @Column(nullable = false)
    private int potCompletedCount;

    /** 현재 멤버가 처음 2명이 된 회차 날짜. 같이 참은 일수의 1일째다 (ROOM-02 19번). */
    private LocalDate togetherStartedDate;

    private LocalDateTime deletedAt;

    public static Room create(String name, RoomGoal goal, int memberLimit, int potGoalAmount) {
        Room room = new Room();
        room.name = name;
        room.goal = goal;
        room.memberLimit = memberLimit;
        room.potGoalAmount = potGoalAmount;
        return room;
    }

    public void assignOwner(Long memberId) {
        ownerMemberId = memberId;
    }

    /** 한 솥을 다 채울 때마다 완성 횟수를 +1 한다 (POT-01 10번). 방 행 락 안에서 부른다. */
    public void completePot() {
        potCompletedCount++;
    }

    /** 현재 멤버가 2명 이상이 되면 부른다. 처음 한 번만 기록한다 (ROOM-02 20번, Q-105). 방 행 락 안에서 부른다. */
    public void startTogether(LocalDate roundDate) {
        if (togetherStartedDate == null) {
            togetherStartedDate = roundDate;
        }
    }

    public void delete(LocalDateTime deletedAt) {
        this.deletedAt = deletedAt;
    }

    public boolean isDeleted() {
        return deletedAt != null;
    }
}
