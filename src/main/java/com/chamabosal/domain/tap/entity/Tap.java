package com.chamabosal.domain.tap.entity;

import com.chamabosal.domain.room.entity.RoomMember;
import com.chamabosal.domain.round.entity.Round;
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

/** 이마 톡톡 한 세트. 행이 생기면 그 사람의 그 회차 세트는 끝난다 (RESULT-03 1·3번). 받는 사람은 회차의 지목된 사람이다. */
@Getter
@Entity
@Table(name = "taps", uniqueConstraints = {
        @UniqueConstraint(name = "uk_taps_round_from_member", columnNames = {"round_id", "from_member_id"})
})
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Tap extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "round_id", nullable = false)
    private Round round;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "from_member_id", nullable = false)
    private RoomMember fromMember;

    /** 0이면 건너뛰기. 0번 행도 다시 못 치게 남긴다. */
    @Column(nullable = false)
    private int count;

    public static Tap create(Round round, RoomMember fromMember, int count) {
        Tap tap = new Tap();
        tap.round = round;
        tap.fromMember = fromMember;
        tap.count = count;
        return tap;
    }
}
