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

        @Schema(description = "나간 지 6개월이 안 된 방이면 내 예전 닉네임. 지금 다른 멤버가 쓰고 있으면 null", example = "지수")
        String previousNickname
) {

    /** 막힌 사유가 드러나지 않게 상태 말고는 모두 비운다. */
    public static InvitePreviewResponse unavailable() {
        return new InvitePreviewResponse(InviteStatus.UNAVAILABLE, null, null, null);
    }

    public record RoomSummary(
            Long roomId,
            String name,
            RoomGoal goal,

            @Schema(description = "절약 목표별 제목", example = "배달 끊기 방")
            String goalTitle,

            @Schema(description = "절약 목표별 설명. 줄바꿈은 \\n", example = "배달 대신 집밥 먹고\n공양미 한 솥까지 함께 가요")
            String goalDescription,

            int memberCount,
            int memberLimit
    ) {

        public static RoomSummary from(Room room, int memberCount) {
            RoomGoal goal = room.getGoal();
            return new RoomSummary(room.getId(), room.getName(), goal, goal.title(), goal.description(),
                    memberCount, room.getMemberLimit());
        }
    }
}
