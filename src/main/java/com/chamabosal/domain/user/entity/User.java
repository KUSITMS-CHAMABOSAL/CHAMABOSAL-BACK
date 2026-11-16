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

    /** 새 컬럼은 이미 있는 사용자도 켜진 상태로 생기도록 DB 기본값을 둔다 (USER-03 2번). */
    @Column(nullable = false, columnDefinition = "boolean default true")
    private boolean isResultPushEnabled = true;

    @Column(nullable = false, columnDefinition = "boolean default true")
    private boolean isVoteOpenedPushEnabled = true;

    @Column(nullable = false, columnDefinition = "boolean default true")
    private boolean isCertReminderPushEnabled = true;

    /**
     * 더 쓰지 않는 예전 인증·투표 알림 토글 (토글 4종으로 나눔, Q-98). 서버 DB에 NOT NULL·기본값 없는 컬럼으로 남아 있어
     * 매핑을 지우면 새 사용자 저장이 실패한다. 출시 전 컬럼을 지울 때 함께 지운다.
     */
    @Getter(AccessLevel.NONE)
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

    /** 보낸 값만 바꾼다 (USER-03 1번). */
    public void updatePushSettings(Boolean isPokePushEnabled, Boolean isResultPushEnabled, Boolean isVoteOpenedPushEnabled,
                                   Boolean isCertReminderPushEnabled) {
        if (isPokePushEnabled != null) {
            this.isPokePushEnabled = isPokePushEnabled;
        }
        if (isResultPushEnabled != null) {
            this.isResultPushEnabled = isResultPushEnabled;
        }
        if (isVoteOpenedPushEnabled != null) {
            this.isVoteOpenedPushEnabled = isVoteOpenedPushEnabled;
        }
        if (isCertReminderPushEnabled != null) {
            this.isCertReminderPushEnabled = isCertReminderPushEnabled;
        }
    }

    public void releaseTossAnonKey() {
        tossAnonKey = null;
    }
}
