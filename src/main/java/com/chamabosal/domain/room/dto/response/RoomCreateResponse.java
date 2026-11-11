package com.chamabosal.domain.room.dto.response;

import com.chamabosal.domain.room.entity.BosalColor;
import com.chamabosal.domain.room.entity.RoomMember;

public record RoomCreateResponse(Long roomId, Long memberId, String nickname, BosalColor color) {

    public static RoomCreateResponse from(RoomMember member) {
        return new RoomCreateResponse(member.getRoom().getId(), member.getId(), member.getNickname(), member.getColor());
    }
}
