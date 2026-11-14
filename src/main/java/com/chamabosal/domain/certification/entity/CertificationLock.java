package com.chamabosal.domain.certification.entity;

/** 인증을 바꿀 수 없는 사유 (CERT-02 7번, CERT-03). */
public enum CertificationLock {
    EDIT_TIME_OVER,
    VOTE_STARTED
}
