package com.chamabosal.domain.round.entity;

import com.chamabosal.domain.room.entity.RoomMember;
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
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 투표 시작 시점의 명단 스냅샷. 시작 뒤 들어오거나 나가도 바뀌지 않는다. */
@Getter
@Entity
@Table(name = "round_participants", uniqueConstraints = {
        @UniqueConstraint(name = "uk_round_participants_round_member", columnNames = {"round_id", "member_id"})
})
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RoundParticipant extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "round_id", nullable = false)
    private Round round;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "member_id", nullable = false)
    private RoomMember member;

    /** 휴식 중이거나, 그날 들어와서 인증하지 않았으면 후보가 아니다. */
    @Column(nullable = false)
    private boolean isCandidate;

    /** 투표 시작 시점까지 인증했으면 투표권이 있다. */
    @Column(nullable = false)
    private boolean canVote;

    @Column(nullable = false)
    private boolean isResting;

    public static RoundParticipant create(Round round, RoomMember member, boolean isCandidate, boolean canVote,
                                          boolean isResting) {
        RoundParticipant participant = new RoundParticipant();
        participant.round = round;
        participant.member = member;
        participant.isCandidate = isCandidate;
        participant.canVote = canVote;
        participant.isResting = isResting;
        return participant;
    }
}
