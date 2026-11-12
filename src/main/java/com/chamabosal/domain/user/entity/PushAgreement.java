package com.chamabosal.domain.user.entity;

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

/** 행이 있으면 동의. 거부는 저장하지 않고, 토스 앱 설정에서 끈 것은 서버가 알 수 없다. */
@Getter
@Entity
@Table(name = "push_agreements", uniqueConstraints = {
        @UniqueConstraint(name = "uk_push_agreements_user_type", columnNames = {"user_id", "type"})
})
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PushAgreement extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    /** MySQL ENUM이나 값 목록 CHECK 없이 varchar로 만든다. 값을 추가해도 ddl-auto update가 막지 않는다. */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, columnDefinition = "varchar(30)")
    private PushAgreementType type;

    @Column(nullable = false)
    private LocalDateTime agreedAt;

    public static PushAgreement create(User user, PushAgreementType type, LocalDateTime agreedAt) {
        PushAgreement agreement = new PushAgreement();
        agreement.user = user;
        agreement.type = type;
        agreement.agreedAt = agreedAt;
        return agreement;
    }
}
