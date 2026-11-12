package com.chamabosal.domain.user.entity;

import java.util.Arrays;
import java.util.Optional;

/** 토스 알림 동의를 받는 알림 종류. 지금은 인증 마감 리마인드만 받는다 (Q-14). */
public enum PushAgreementType {
    CERT_REMINDER;

    public static Optional<PushAgreementType> find(String name) {
        return Arrays.stream(values()).filter(type -> type.name().equals(name)).findFirst();
    }
}
