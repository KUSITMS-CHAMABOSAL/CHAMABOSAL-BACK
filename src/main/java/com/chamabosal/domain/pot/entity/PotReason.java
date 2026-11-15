package com.chamabosal.domain.pot.entity;

/** 솥에 넣거나 뺀 사유. 회수(RECALL)는 원인 인증 ID를 남긴다. */
public enum PotReason {
    CERTIFICATION,
    VOTE,
    TAP,
    REACTION,
    RECALL
}
