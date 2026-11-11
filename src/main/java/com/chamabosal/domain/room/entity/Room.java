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
import java.time.LocalDateTime;

@Getter
@Entity
@Table(name = "rooms")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Room extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 12)
    private String name;

    /** MySQL ENUM이나 값 목록 CHECK 없이 varchar로 만든다. 값을 추가해도 ddl-auto update가 막지 않는다. */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, columnDefinition = "varchar(20)")
    private RoomGoal goal;

    @Column(nullable = false)
    private int memberLimit;

    /** 방을 먼저 저장해야 멤버를 만들 수 있어 NULL을 허용한다. 같은 요청 안에서 채운다. */
    private Long ownerMemberId;

    @Column(nullable = false)
    private int potCompletedCount;

    private LocalDateTime deletedAt;

    public static Room create(String name, RoomGoal goal, int memberLimit) {
        Room room = new Room();
        room.name = name;
        room.goal = goal;
        room.memberLimit = memberLimit;
        return room;
    }

    public void assignOwner(Long memberId) {
        ownerMemberId = memberId;
    }

    public boolean isDeleted() {
        return deletedAt != null;
    }
}
