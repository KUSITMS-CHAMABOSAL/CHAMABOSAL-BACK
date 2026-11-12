package com.chamabosal.domain.room.service;

/** 초대 링크를 연 사용자의 입장 판단 결과 (INVITE-02). */
public enum InviteStatus {
    JOINABLE,
    ALREADY_MEMBER,
    ROOM_LIMIT,
    ROOM_FULL,
    UNAVAILABLE
}
