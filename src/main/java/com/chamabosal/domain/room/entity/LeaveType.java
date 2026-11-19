package com.chamabosal.domain.room.entity;

public enum LeaveType {
    LEFT,
    KICKED,
    DATA_DELETED,
    /** 목표 달성 뒤 방장이 [방 닫기]를 골라 멤버 전원이 나감 (ROOM-09 6번) */
    ROOM_CLOSED
}
