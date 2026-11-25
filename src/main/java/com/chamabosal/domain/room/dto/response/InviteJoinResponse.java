package com.chamabosal.domain.room.dto.response;

import com.chamabosal.domain.room.entity.BosalColor;
import com.chamabosal.domain.room.entity.RoomMember;
import io.swagger.v3.oas.annotations.media.Schema;

public record InviteJoinResponse(
        Long roomId,
        Long memberId,
        String nickname,
        BosalColor color,
        @Schema(description = "처음 보살을 받아 방에 등록했는지 (가입 완료 지표용). 나간 방을 포함해 방 멤버였던 적이 있으면 false") boolean isNewUser
) {

    public static InviteJoinResponse from(RoomMember member, boolean isNewUser) {
        return new InviteJoinResponse(member.getRoom().getId(), member.getId(), member.getNickname(), member.getColor(), isNewUser);
    }
}
