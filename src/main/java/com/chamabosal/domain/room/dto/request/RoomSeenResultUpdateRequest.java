package com.chamabosal.domain.room.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

public record RoomSeenResultUpdateRequest(
        @Schema(description = "본 결과의 회차 ID. 그 회차와 이전 회차를 모두 본 것으로 처리", example = "99")
        @NotNull(message = "본 결과의 회차를 보내 주세요.")
        Long roundId
) {
}
