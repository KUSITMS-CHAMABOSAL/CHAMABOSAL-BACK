package com.chamabosal.domain.user.entity;

import java.util.Arrays;
import java.util.Optional;

/** 토스 알림 동의를 받는 알림 종류. 알림 템플릿마다 동의문이 따로 있어 종류별로 저장한다 (Q-14). */
public enum PushAgreementType {
    POKE,
    RESULT,
    VOTE_OPENED,
    CERT_REMINDER;

    public static Optional<PushAgreementType> find(String name) {
        return Arrays.stream(values()).filter(type -> type.name().equals(name)).findFirst();
    }
}
