package com.chamabosal.domain.tap.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record MoktakCompleteRequest(
        @Schema(description = "두드린 횟수. 그 회차에 받은 톡톡 합계 이상이어야 보상을 받음", example = "201")
        @NotNull(message = "두드린 횟수를 입력해 주세요.")
        @Min(value = 0, message = "두드린 횟수는 0번 이상입니다.")
        Integer count
) {
}
