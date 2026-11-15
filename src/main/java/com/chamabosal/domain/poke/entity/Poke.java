package com.chamabosal.domain.poke.entity;

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

/** 회차마다 보낸 사람 → 받는 사람의 누적 콕 횟수 (POKE-01 7번). 받는 사람의 "콕 ×n"은 이 행들의 합이다. */
@Getter
@Entity
@Table(name = "pokes", uniqueConstraints = {
        @UniqueConstraint(name = "uk_pokes_round_from_to", columnNames = {"round_id", "from_member_id", "to_member_id"})
})
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Poke extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "round_id", nullable = false)
    private Round round;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "from_member_id", nullable = false)
    private RoomMember fromMember;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "to_member_id", nullable = false)
    private RoomMember toMember;

    @Column(nullable = false)
    private int count;

    public static Poke create(Round round, RoomMember fromMember, RoomMember toMember, int count) {
        Poke poke = new Poke();
        poke.round = round;
        poke.fromMember = fromMember;
        poke.toMember = toMember;
        poke.count = count;
        return poke;
    }

    /** 앱 안 누적 횟수에는 제한이 없다 (POKE-01 5번). */
    public void add(int count) {
        this.count += count;
    }
}
