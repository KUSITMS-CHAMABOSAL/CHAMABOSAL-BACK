package com.chamabosal.domain.reaction.entity;

import com.chamabosal.domain.certification.entity.Certification;
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
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 인증 리액션. 한 사람은 한 인증에 하나만 남기고, 바꾸면 종류를 고치고 취소하면 행을 지운다 (REACT-01 1~3번). */
@Getter
@Entity
@Table(name = "reactions", uniqueConstraints = {
        @UniqueConstraint(name = "uk_reactions_certification_member", columnNames = {"certification_id", "member_id"})
})
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Reaction extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "certification_id", nullable = false)
    private Certification certification;

    /** 누른 사람 */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "member_id", nullable = false)
    private RoomMember member;

    /** MySQL ENUM이나 값 목록 CHECK 없이 varchar로 만든다. 값을 추가해도 ddl-auto update가 막지 않는다. */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, columnDefinition = "varchar(20)")
    private ReactionType type;

    public static Reaction create(Certification certification, RoomMember member, ReactionType type) {
        Reaction reaction = new Reaction();
        reaction.certification = certification;
        reaction.member = member;
        reaction.type = type;
        return reaction;
    }

    public void change(ReactionType type) {
        this.type = type;
    }
}
