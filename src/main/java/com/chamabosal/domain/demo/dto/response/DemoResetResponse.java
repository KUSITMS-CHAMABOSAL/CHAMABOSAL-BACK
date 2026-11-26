package com.chamabosal.domain.demo.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

public record DemoResetResponse(
        @Schema(description = "시연 방 1 (메인 시연) ID")
        Long mainRoomId,

        @Schema(description = "시연 방 2 (목탁 체험) ID")
        Long moktakRoomId
) {
}
