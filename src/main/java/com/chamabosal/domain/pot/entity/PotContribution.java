package com.chamabosal.domain.pot.entity;

import com.chamabosal.domain.room.entity.RoomMember;
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
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 멤버가 솥에 넣거나 뺀 석. 축하 화면의 넣은 석과 순위를 이 합으로 계산한다 (POT-01 14번). */
@Getter
@Entity
@Table(name = "pot_contributions",
        uniqueConstraints = @UniqueConstraint(name = "uk_pot_contributions_member_reason_ref",
                columnNames = {"member_id", "reason", "ref_id"}),
        indexes = @Index(name = "idx_pot_contributions_pot", columnList = "pot_id"))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PotContribution extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "pot_id", nullable = false)
    private RoomPot pot;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "member_id", nullable = false)
    private RoomMember member;

    @Column(nullable = false)
    private int delta;

    /** MySQL ENUM이나 값 목록 CHECK 없이 varchar로 만든다. 값을 추가해도 ddl-auto update가 막지 않는다. */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, columnDefinition = "varchar(20)")
    private PotReason reason;

    /** 인증·투표·톡톡 ID. 리액션과 회수는 인증 ID */
    @Column(nullable = false)
    private Long refId;

    public static PotContribution create(RoomPot pot, RoomMember member, int delta, PotReason reason, Long refId) {
        PotContribution contribution = new PotContribution();
        contribution.pot = pot;
        contribution.member = member;
        contribution.delta = delta;
        contribution.reason = reason;
        contribution.refId = refId;
        return contribution;
    }
}
