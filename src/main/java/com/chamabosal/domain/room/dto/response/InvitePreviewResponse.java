package com.chamabosal.domain.room.dto.response;

import com.chamabosal.domain.room.entity.Room;
import com.chamabosal.domain.room.entity.RoomGoal;
import com.chamabosal.domain.room.service.InviteStatus;
import io.swagger.v3.oas.annotations.media.Schema;

public record InvitePreviewResponse(
        @Schema(description = "입장 판단 결과")
        InviteStatus status,

        @Schema(description = "방 정보. UNAVAILABLE이면 null")
        RoomSummary room,

        @Schema(description = "초대한 사람 닉네임. 초대한 사람이 나갔으면 null", example = "민지")
        String inviterNickname,

        @Schema(description = "나간 지 6개월이 안 된 방이면 내 예전 닉네임", example = "지수")
        String previousNickname
) {

    /** 막힌 사유가 드러나지 않게 상태 말고는 모두 비운다. */
    public static InvitePreviewResponse unavailable() {
        return new InvitePreviewResponse(InviteStatus.UNAVAILABLE, null, null, null);
    }

    public record RoomSummary(Long roomId, String name, RoomGoal goal, int memberCount, int memberLimit) {

        public static RoomSummary from(Room room, int memberCount) {
            return new RoomSummary(room.getId(), room.getName(), room.getGoal(), memberCount, room.getMemberLimit());
        }
    }
}
