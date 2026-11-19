package com.chamabosal.domain.tap.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record TapSendRequest(
        @Schema(description = "친 횟수 0 이상, 상한 없음. 0이면 건너뛰기", example = "42")
        @NotNull(message = "톡톡 횟수를 입력해 주세요.")
        @Min(value = 0, message = "톡톡 횟수는 0번 이상이에요.")
        Integer count
) {
}
