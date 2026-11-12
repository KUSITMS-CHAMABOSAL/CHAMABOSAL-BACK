package com.chamabosal.domain.room.dto.response;

import com.chamabosal.domain.room.entity.BosalColor;
import com.chamabosal.domain.room.entity.RoomMember;

public record InviteJoinResponse(Long roomId, Long memberId, String nickname, BosalColor color) {

    public static InviteJoinResponse from(RoomMember member) {
        return new InviteJoinResponse(member.getRoom().getId(), member.getId(), member.getNickname(), member.getColor());
    }
}
