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

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 내 데이터를 삭제한 사용자는 NULL로 남아 다른 테이블의 참조를 유지한다. */
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

    /** 내 데이터 삭제 (USER-05). 식별키를 바로 비워 같은 키로 오면 새 사용자가 된다. 행은 다른 기록의 참조용으로 남긴다. */
    public void deleteData(LocalDateTime deletedAt) {
        this.deletedAt = deletedAt;
        this.tossAnonKey = null;
    }

    public void updatePushSettings(Boolean isPokePushEnabled, Boolean isActivityPushEnabled) {
        if (isPokePushEnabled != null) {
            this.isPokePushEnabled = isPokePushEnabled;
        }
        if (isActivityPushEnabled != null) {
            this.isActivityPushEnabled = isActivityPushEnabled;
        }
    }

    public void releaseTossAnonKey() {
        tossAnonKey = null;
    }
}
