package com.chamabosal.domain.vote.entity;

import com.chamabosal.domain.room.entity.RoomMember;
import com.chamabosal.domain.round.entity.Round;
import com.chamabosal.global.common.entity.BaseEntity;
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

@Getter
@Entity
@Table(name = "votes", uniqueConstraints = {
        @UniqueConstraint(name = "uk_votes_round_voter", columnNames = {"round_id", "voter_member_id"})
})
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Vote extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "round_id", nullable = false)
    private Round round;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "voter_member_id", nullable = false)
    private RoomMember voterMember;

    /** NULL이면 "오늘은 모두 보살". */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "target_member_id")
    private RoomMember targetMember;

    /** 1인 1표, 변경 불가라 수정 메서드를 두지 않는다. */
    public static Vote create(Round round, RoomMember voterMember, RoomMember targetMember) {
        Vote vote = new Vote();
        vote.round = round;
        vote.voterMember = voterMember;
        vote.targetMember = targetMember;
        return vote;
    }
}
