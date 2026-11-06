package com.chamabosal.domain.user.entity;

import com.chamabosal.global.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Getter
@Entity
@Table(name = "users")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class User extends BaseEntity {

    private static final int RESTORABLE_MONTHS = 6;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 정리된 사용자는 NULL로 남아 다른 테이블의 참조를 유지한다. */
    @Column(unique = true)
    private String tossAnonKey;

    @Column(nullable = false)
    private boolean isPokePushEnabled = true;

    @Column(nullable = false)
    private boolean isActivityPushEnabled = true;

    private LocalDateTime deletedAt;

    public static User create(String tossAnonKey) {
        User user = new User();
        user.tossAnonKey = tossAnonKey;
        return user;
    }

    public boolean isDeleted() {
        return deletedAt != null;
    }

    public boolean isRestorable(LocalDateTime now) {
        return !now.isAfter(deletedAt.plusMonths(RESTORABLE_MONTHS));
    }

    public void restore() {
        deletedAt = null;
    }

    public void releaseTossAnonKey() {
        tossAnonKey = null;
    }
}
