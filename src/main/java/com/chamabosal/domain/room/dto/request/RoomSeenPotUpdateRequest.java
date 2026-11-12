package com.chamabosal.domain.room.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record RoomSeenPotUpdateRequest(
        @Schema(description = "몇 번째 솥 축하까지 봤는지. 오늘 탭의 room.potCompletedCount를 그대로 보냄", example = "2")
        @NotNull(message = "본 완성 횟수를 입력해 주세요.")
        @Min(value = 0, message = "본 완성 횟수는 0 이상입니다.")
        Integer seenPotCount
) {
}
