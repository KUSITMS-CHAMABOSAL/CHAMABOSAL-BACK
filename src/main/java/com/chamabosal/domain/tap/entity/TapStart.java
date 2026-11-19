package com.chamabosal.domain.tap.entity;

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

/**
 * 이마 톡톡을 시작했다는 기록 (RESULT-03 10번). `PICKED`일 때만 만들 수 있어서, 행이 있으면 방어 전에 시작한 것이다.
 * 방어로 `DEFENDED`가 된 뒤에는 이 행이 있는 사람만 톡톡을 보낼 수 있다.
 */
@Getter
@Entity
@Table(name = "tap_starts", uniqueConstraints = {
        @UniqueConstraint(name = "uk_tap_starts_round_member", columnNames = {"round_id", "member_id"})
})
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TapStart extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "round_id", nullable = false)
    private Round round;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "member_id", nullable = false)
    private RoomMember member;

    public static TapStart create(Round round, RoomMember member) {
        TapStart start = new TapStart();
        start.round = round;
        start.member = member;
        return start;
    }
}
