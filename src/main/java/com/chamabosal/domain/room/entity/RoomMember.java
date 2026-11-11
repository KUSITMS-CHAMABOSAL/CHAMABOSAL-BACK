package com.chamabosal.domain.room.entity;

import com.chamabosal.domain.user.entity.User;
import com.chamabosal.global.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Getter
@Entity
@Table(name = "room_members", indexes = {
        @Index(name = "idx_room_members_room_left", columnList = "room_id, left_at"),
        @Index(name = "idx_room_members_user_left", columnList = "user_id, left_at")
})
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RoomMember extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "room_id", nullable = false)
    private Room room;

    /** 내 데이터 삭제 후 6개월이 지나면 연결을 끊어 NULL이 된다. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    @Column(nullable = false, length = 8)
    private String nickname;

    /** MySQL ENUM이나 값 목록 CHECK 없이 varchar로 만든다. 값을 추가해도 ddl-auto update가 막지 않는다. */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, columnDefinition = "varchar(20)")
    private BosalColor color;

    @Column(nullable = false)
    private LocalDateTime joinedAt;

    /** NULL이면 현재 멤버. */
    private LocalDateTime leftAt;

    @Enumerated(EnumType.STRING)
    @Column(columnDefinition = "varchar(20)")
    private LeaveType leaveType;

    @Column(unique = true, length = 64)
    private String inviteToken;

    private LocalDate seenResultRoundDate;

    @Column(nullable = false)
    private int seenPotCount;

    @Column(nullable = false)
    private int pickedStreak;

    private LocalDate restingRoundDate;

    public static RoomMember create(Room room, User user, String nickname, BosalColor color, LocalDateTime joinedAt) {
        RoomMember member = new RoomMember();
        member.room = room;
        member.user = user;
        member.nickname = nickname;
        member.color = color;
        member.joinedAt = joinedAt;
        return member;
    }

    public void changeNickname(String nickname) {
        this.nickname = nickname;
    }

    /** 현재 멤버이거나 나간 지 holdMonths가 지나지 않았으면 이 닉네임을 다른 사람이 쓸 수 없다. left_at + 기간 시각까지 포함한다. */
    public boolean holdsNickname(LocalDateTime now, int holdMonths) {
        return leftAt == null || !now.isAfter(leftAt.plusMonths(holdMonths));
    }

    public void leave(LocalDateTime leftAt, LeaveType leaveType) {
        this.leftAt = leftAt;
        this.leaveType = leaveType;
    }
}
