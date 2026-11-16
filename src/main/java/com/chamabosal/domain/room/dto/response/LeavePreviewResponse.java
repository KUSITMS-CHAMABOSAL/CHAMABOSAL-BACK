package com.chamabosal.domain.room.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

/** 나가기 확인 화면의 사라질 공양미 (RICE-03 6번). */
public record LeavePreviewResponse(
        @Schema(description = "지금 나가면 사라질 공양미 석. 목표를 채운 방이거나 잔액이 0석 이하면 0", example = "120") int riceToLose
) {
}
