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
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

@Getter
@Entity
@Table(name = "rounds", uniqueConstraints = {
        @UniqueConstraint(name = "uk_rounds_room_round_date", columnNames = {"room_id", "round_date"})
})
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Round extends BaseEntity {

    private static final LocalTime ROUND_START = LocalTime.of(3, 0);
    private static final LocalTime VOTE_DEADLINE = LocalTime.of(15, 0);

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

    /** 지목된 사람이 결과를 처음 조회한 시각. 방어 마감(+2분) 기준이다. */
    private LocalDateTime pickedSeenAt;

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
}
