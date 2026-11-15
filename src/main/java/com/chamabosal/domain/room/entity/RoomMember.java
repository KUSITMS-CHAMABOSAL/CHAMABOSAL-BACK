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
import org.hibernate.annotations.DynamicUpdate;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Getter
@Entity
@Table(name = "room_members", indexes = {
        @Index(name = "idx_room_members_room_left", columnList = "room_id, left_at"),
        @Index(name = "idx_room_members_user_left", columnList = "user_id, left_at")
})
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@DynamicUpdate // 확인 횟수는 조건부 update로 따로 올려서, 다른 변경을 저장할 때 바뀐 컬럼만 써야 옛 값으로 덮어쓰지 않는다.
public class RoomMember extends BaseEntity {

    /** 나간 지 이 기간이 지나지 않으면 닉네임을 잡고 있고, 다시 들어올 때 이 행을 다시 쓴다. */
    private static final int RETENTION_MONTHS = 6;
    private static final int REST_STREAK = 3;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "room_id", nullable = false)
    private Room room;

    /** 내 데이터를 삭제하면 바로 연결을 끊어 NULL이 된다 (USER-05 6번). 나간 멤버의 사용자는 비어 있을 수 있다. */
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

    /** 들어오기 전의 한 솥 축하는 보여주지 않도록 확인 상태를 방의 현재 완성 횟수로 채운다. 결과 확인 상태는 fillSeenResult로 채운다. */
    public static RoomMember create(Room room, User user, String nickname, BosalColor color, LocalDateTime joinedAt) {
        RoomMember member = new RoomMember();
        member.room = room;
        member.user = user;
        member.nickname = nickname;
        member.color = color;
        member.joinedAt = joinedAt;
        member.seenPotCount = room.getPotCompletedCount();
        return member;
    }

    public void changeNickname(String nickname) {
        this.nickname = nickname;
    }

    public boolean isCurrent() {
        return leftAt == null;
    }

    public boolean isKicked() {
        return leaveType == LeaveType.KICKED;
    }

    /** 현재 멤버이거나 나간 지 6개월이 지나지 않았으면 이 닉네임을 다른 사람이 쓸 수 없다. left_at + 6개월 시각까지 포함한다. */
    public boolean holdsNickname(LocalDateTime now) {
        return isCurrent() || isWithinRetention(now);
    }

    /** 스스로 나가거나 내 데이터 삭제로 나간 지 6개월이 지나지 않았으면 이 행으로 다시 들어온다. */
    public boolean canRejoin(LocalDateTime now) {
        return !isCurrent() && !isKicked() && isWithinRetention(now);
    }

    /** 지목·휴식·초대 토큰은 그대로 이어진다. */
    public void rejoin(String nickname, BosalColor color, LocalDateTime joinedAt) {
        this.leftAt = null;
        this.leaveType = null;
        this.nickname = nickname;
        this.color = color;
        this.joinedAt = joinedAt;
        this.seenPotCount = room.getPotCompletedCount();
    }

    /** 들어오기 전의 결과는 보여주지 않도록 입장할 때 가장 최근 발표 회차 날짜로 채운다. 발표된 회차가 없으면 NULL이다. */
    public void fillSeenResult(LocalDate latestAnnouncedRoundDate) {
        this.seenResultRoundDate = latestAnnouncedRoundDate;
    }

    /** 가장 최근 발표 회차를 아직 안 봤는지 (RESULT-02). 본 날짜가 없으면 안 본 것이다. */
    public boolean hasUnseenResult(LocalDate latestAnnouncedRoundDate) {
        return seenResultRoundDate == null || seenResultRoundDate.isBefore(latestAnnouncedRoundDate);
    }

    /** 방의 한 솥 완성 횟수가 본 횟수보다 크면 안 본 축하가 있다 (POT-01). */
    public boolean hasUnseenPot() {
        return room.getPotCompletedCount() > seenPotCount;
    }

    /** 토큰은 한 번 정해지면 바뀌지 않는다. */
    public void issueInviteToken(String inviteToken) {
        if (this.inviteToken == null) {
            this.inviteToken = inviteToken;
        }
    }

    private boolean isWithinRetention(LocalDateTime now) {
        return !now.isAfter(leftAt.plusMonths(RETENTION_MONTHS));
    }

    /**
     * 휴식 날짜가 이 회차 날짜보다 이전이면 휴식이 끝난 것이라 연속 지목과 휴식 날짜를 비운다. 휴식 날짜에 투표가 없었어도 휴식을
     * 쓴 것으로 본다 (RESULT-04 4·5번). 투표 시작 때 불러 다음 투표가 시작되면 0이 되게 한다.
     */
    public void finishRestBefore(LocalDate roundDate) {
        if (restingRoundDate != null && restingRoundDate.isBefore(roundDate)) {
            restingRoundDate = null;
            pickedStreak = 0;
        }
    }

    /**
     * 결과 발표 때 부른다 (RESULT-04). 이번 회차가 휴식 회차였으면 먼저 휴식을 정리한다 (5번).
     * 그다음 지목되면 +1하고 3회 연속이면 다음 회차 날짜를 휴식으로 둔다. 아니면 0이다 (1·3·4번).
     */
    public void applyRoundResult(LocalDate roundDate, boolean isPicked) {
        finishRestBefore(roundDate.plusDays(1));
        if (!isPicked) {
            pickedStreak = 0;
            return;
        }
        pickedStreak++;
        if (pickedStreak == REST_STREAK) {
            restingRoundDate = roundDate.plusDays(1);
        }
    }

    public void leave(LocalDateTime leftAt, LeaveType leaveType) {
        this.leftAt = leftAt;
        this.leaveType = leaveType;
    }
}
