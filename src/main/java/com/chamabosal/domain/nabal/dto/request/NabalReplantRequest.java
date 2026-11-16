package com.chamabosal.domain.nabal.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;

public record NabalReplantRequest(
        @Schema(description = "심을 나발 번호(0~37). 빠진 나발만, 같은 번호는 한 번만", example = "[3, 7]")
        @NotEmpty(message = "심을 나발을 골라 주세요.")
        List<@NotNull(message = NABAL_NUMBER_MESSAGE) @Min(value = 0, message = NABAL_NUMBER_MESSAGE)
                @Max(value = 37, message = NABAL_NUMBER_MESSAGE) Integer> nabalNumbers
) {

    public static final String NABAL_NUMBER_MESSAGE = "나발 번호는 0~37이에요.";
}
