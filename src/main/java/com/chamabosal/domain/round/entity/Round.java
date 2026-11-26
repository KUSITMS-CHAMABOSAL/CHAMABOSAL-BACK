package com.chamabosal.domain.round.entity;

import com.chamabosal.domain.room.entity.Room;
import com.chamabosal.domain.room.entity.RoomMember;
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
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.DynamicUpdate;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

@Getter
@Entity
@DynamicUpdate // 처음 연 시각은 조건부 update로 따로 남겨서, 다른 변경을 저장할 때 바뀐 컬럼만 써야 옛 값으로 덮어쓰지 않는다.
@Table(name = "rounds", uniqueConstraints = {
        @UniqueConstraint(name = "uk_rounds_room_round_date", columnNames = {"room_id", "round_date"})
})
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Round extends BaseEntity {

    private static final LocalTime ROUND_START = LocalTime.of(3, 0);
    private static final LocalTime VOTE_DEADLINE = LocalTime.of(15, 0);
    public static final int TAP_HOURS = 5;
    private static final int DEFENSE_MINUTES = 2;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "room_id", nullable = false)
    private Room room;

    /** KST 03:00부터 다음 날 02:59까지가 같은 회차 날짜다. */
    @Column(nullable = false)
    private LocalDate roundDate;

    /** MySQL ENUM이나 값 목록 CHECK 없이 varchar로 만든다. 값을 추가해도 ddl-auto update가 막지 않는다. */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, columnDefinition = "varchar(20)")
    private RoundStatus status;

    /** `NO_VOTE`로 확정되면 그 시각을 기록해 사진 삭제 기준으로 쓴다. */
    private LocalDateTime voteStartedAt;

    private LocalDateTime voteClosedAt;

    @Enumerated(EnumType.STRING)
    @Column(columnDefinition = "varchar(20)")
    private RoundResultType resultType;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "picked_member_id")
    private RoomMember pickedMember;

    /** 멤버가 나가도 지목 횟수를 집계하려고 사용자도 남긴다. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "picked_user_id")
    private User pickedUser;

    /** 지목된 사람이 결과를 처음 연 시각. 방어 마감(+2분) 기준이다 (SHIELD-02 3번). */
    private LocalDateTime pickedSeenAt;

    /** 지목된 사람이 목탁 수행을 끝낸 시각(끝까지 했거나 [종료하기]). 있으면 다시 수행할 수 없다 (RESULT-03 9번). */
    private LocalDateTime moktakEndedAt;

    /** 지목된 사람을 뺀 회차 멤버 전원이 톡톡을 보낸 시각. 있으면 방어 시간 뒤 일찍 전달한다 (RESULT-03 5·11번). */
    private LocalDateTime tapsCompletedAt;

    /** 전원 인증·투표 이벤트와 03:00·15:00 배치가 같은 회차를 동시에 바꾸면 나중 쪽을 실패시킨다. */
    @Version
    @Column(nullable = false)
    private Long version;

    /** 회차 행은 그날 방에서 첫 인증이나 첫 콕이 있을 때 만든다. */
    public static Round create(Room room, LocalDate roundDate) {
        Round round = new Round();
        round.room = room;
        round.roundDate = roundDate;
        round.status = RoundStatus.CERTIFYING;
        return round;
    }

    /** 03:00 전이면 전날 회차, 03:00부터 그날 회차다. */
    public static LocalDate dateOf(LocalDateTime time) {
        LocalDate date = time.toLocalDate();
        return time.toLocalTime().isBefore(ROUND_START) ? date.minusDays(1) : date;
    }

    /** 그 회차가 시작하는 시각 (그날 03:00). 회차는 다음 날 03:00 전까지다. */
    public static LocalDateTime startOf(LocalDate roundDate) {
        return roundDate.atTime(ROUND_START);
    }

    public boolean isCertifying() {
        return status == RoundStatus.CERTIFYING;
    }

    /** 투표 없이 끝났거나 투표가 마감됐으면 다음 회차 인증이 열린다. */
    public boolean isVoteFinished() {
        return status == RoundStatus.NO_VOTE || voteClosedAt != null;
    }

    public void startVote(LocalDateTime startedAt) {
        this.status = RoundStatus.VOTING;
        this.voteStartedAt = startedAt;
    }

    /** 확정 시각을 vote_started_at에 남겨 사진 삭제 기준으로 쓴다. */
    public void markNoVote(LocalDateTime decidedAt) {
        this.status = RoundStatus.NO_VOTE;
        this.voteStartedAt = decidedAt;
    }

    /** 투표는 늦어도 회차 날짜 다음 날 15:00에 닫힌다. */
    public LocalDateTime getVoteClosesAt() {
        return roundDate.plusDays(1).atTime(VOTE_DEADLINE);
    }

    /** 톡톡은 투표 마감 후 5시간 동안 보내고, 그 시각에 지목된 사람에게 한 번에 전달된다 (RESULT-03 4·5번, Q-92). */
    public LocalDateTime getTapClosesAt() {
        return voteClosedAt.plusHours(TAP_HOURS);
    }

    /** 톡톡 마감 시각 전까지만 보낼 수 있다. 그 시각부터는 전달된 것으로 보아 둘이 겹치지 않는다. */
    public boolean isTapOpen(LocalDateTime now) {
        return now.isBefore(getTapClosesAt());
    }

    /** 결과 판정(RESULT-01)과 마감을 같이 기록한다. pickedMember가 없으면(ALL_BOSAL·ALL_SAFE) 지목 사용자도 비운다. */
    public void announce(LocalDateTime closedAt, RoundResultType resultType, RoomMember pickedMember) {
        this.status = RoundStatus.ANNOUNCED;
        this.voteClosedAt = closedAt;
        this.resultType = resultType;
        this.pickedMember = pickedMember;
        this.pickedUser = pickedMember != null ? pickedMember.getUser() : null;
    }

    /** 투표 시작 뒤에 들어온(다시 들어온 포함) 멤버인지. 그 회차 명단 밖으로 본다 (Q-36). */
    public boolean isJoinedAfterVoteStart(RoomMember member) {
        return voteStartedAt != null && member.getJoinedAt().isAfter(voteStartedAt);
    }

    /**
     * 방어권은 지목된 사람이 결과를 처음 연 순간부터 2분 안에 쓴다. 아직 열지 않았거나, 톡톡 마감 뒤에 처음 열었으면
     * 쓸 수 있는 시각이 없어 null이다 (SHIELD-02 3번, Q-75·Q-77).
     */
    public LocalDateTime getDefenseClosesAt() {
        if (pickedSeenAt == null || !pickedSeenAt.isBefore(getTapClosesAt())) {
            return null;
        }
        return pickedSeenAt.plusMinutes(DEFENSE_MINUTES);
    }

    /** 전원이 톡톡을 보낸 시각을 남긴다. 이미 남았으면 처음 시각을 그대로 둔다 (RESULT-03 11번). */
    public void completeTaps(LocalDateTime completedAt) {
        if (tapsCompletedAt == null) {
            tapsCompletedAt = completedAt;
        }
    }

    /**
     * 받은 톡톡을 전달하는 시각 (RESULT-03 5·6번). 전원이 보냈고 지목된 사람의 방어 시간이 정해졌으면 둘 중 늦은 시각,
     * 아니면 톡톡 마감 시각이다.
     */
    public LocalDateTime getTapDeliveredAt() {
        LocalDateTime defenseClosesAt = getDefenseClosesAt();
        if (tapsCompletedAt == null || defenseClosesAt == null) {
            return getTapClosesAt();
        }
        LocalDateTime early = tapsCompletedAt.isAfter(defenseClosesAt) ? tapsCompletedAt : defenseClosesAt;
        return early.isBefore(getTapClosesAt()) ? early : getTapClosesAt();
    }

    /** 받은 톡톡이 전달됐는지. 방어는 방어 마감 시각까지 되므로(isAfter) 일찍 전달은 그 시각을 넘긴 뒤부터다. */
    public boolean isTapDelivered(LocalDateTime now) {
        if (!isTapOpen(now)) {
            return true;
        }
        LocalDateTime defenseClosesAt = getDefenseClosesAt();
        return tapsCompletedAt != null && defenseClosesAt != null
                && now.isAfter(defenseClosesAt) && !now.isBefore(tapsCompletedAt);
    }

    /** 지목된 사람이 아직 방어권을 쓸 수 있는지. 다른 멤버의 `방어권 고민 중` 표시에 쓴다 (RESULT-02 10번). */
    public boolean isDefensePending(LocalDateTime now) {
        if (resultType != RoundResultType.PICKED || !isTapOpen(now)) {
            return false;
        }
        return pickedSeenAt == null || !now.isAfter(pickedSeenAt.plusMinutes(DEFENSE_MINUTES));
    }

    /** 목탁 수행을 끝낸 것으로 남긴다. 이미 끝났으면 처음 시각을 그대로 둔다. */
    public void endMoktak(LocalDateTime endedAt) {
        if (moktakEndedAt == null) {
            moktakEndedAt = endedAt;
        }
    }

    public boolean isMoktakEnded() {
        return moktakEndedAt != null;
    }

    /** 지목된 사람이 방어권을 쓰면 결과만 `DEFENDED`로 바꾼다. 차순위로 넘기지 않아 지목된 사람은 그대로다 (SHIELD-02 5번). */
    public void defend() {
        this.resultType = RoundResultType.DEFENDED;
    }

        public boolean isVoting() {
        return status == RoundStatus.VOTING;
    }
}
