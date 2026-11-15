package com.chamabosal.domain.tap.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

public record MoktakCompleteResponse(
        @Schema(description = "번뇌 완료 보상. 처음 완료하면 10, 이미 완료했거나 방 인원이 1명이면 0") int riceEarned
) {
}
