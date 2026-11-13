package com.chamabosal.domain.certification.entity;

import com.chamabosal.domain.room.entity.RoomMember;
import com.chamabosal.domain.round.entity.Round;
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
import java.time.LocalDateTime;

@Getter
@Entity
@Table(name = "certifications", uniqueConstraints = {
        @UniqueConstraint(name = "uk_certifications_round_active_member", columnNames = {"round_id", "active_member_id"})
})
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Certification extends BaseEntity {

    private static final int EDITABLE_MINUTES = 10;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "round_id", nullable = false)
    private Round round;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "member_id", nullable = false)
    private RoomMember member;

    /** 삭제되지 않은 인증만 member_id를 가져서, 회차당 활성 인증 1개를 UNIQUE로 막는다. 값은 DB가 계산한다. */
    @Column(insertable = false, updatable = false,
            columnDefinition = "bigint generated always as (case when deleted_at is null then member_id end)")
    private Long activeMemberId;

    /** MySQL ENUM이나 값 목록 CHECK 없이 varchar로 만든다. 값을 추가해도 ddl-auto update가 막지 않는다. */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, columnDefinition = "varchar(20)")
    private CertificationResult result;

    /** 6개월 정리 때 NULL이 된다. */
    @Column(length = 40)
    private String memo;

    /** `FAILED`일 때만 있다. */
    private Integer spentAmount;

    private String imageKey;

    private LocalDateTime imageDeletedAt;

    @Column(nullable = false)
    private LocalDateTime certifiedAt;

    private LocalDateTime deletedAt;

    /** `ENDURED`면 금액을 저장하지 않는다. */
    public static Certification create(Round round, RoomMember member, CertificationResult result, String memo,
                                       Integer spentAmount, LocalDateTime certifiedAt) {
        Certification certification = new Certification();
        certification.round = round;
        certification.member = member;
        certification.result = result;
        certification.memo = memo;
        certification.spentAmount = result == CertificationResult.FAILED ? spentAmount : null;
        certification.certifiedAt = certifiedAt;
        return certification;
    }

    public LocalDateTime getEditableUntil() {
        return certifiedAt.plusMinutes(EDITABLE_MINUTES);
    }
}
