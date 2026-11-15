package com.chamabosal.domain.poke.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record PokeSendRequest(
        @Schema(description = "받는 멤버 ID", example = "12")
        @NotNull(message = "받는 멤버를 입력해 주세요.")
        Long toMemberId,

        @Schema(description = "화면에서 1~2초 모아 보낸 횟수 1~30", example = "5")
        @NotNull(message = "콕 횟수를 입력해 주세요.")
        @Min(value = 1, message = "콕 횟수는 1~30번입니다.")
        @Max(value = 30, message = "콕 횟수는 1~30번입니다.")
        Integer count
) {
}
