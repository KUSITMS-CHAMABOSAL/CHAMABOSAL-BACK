package com.chamabosal.domain.nabal.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

public record NabalSeenUpdateRequest(
        @Schema(description = "연출을 본 마지막 나발 기록 ID (내 정보의 nabal.lastNabalLossId)", example = "12")
        @NotNull(message = "본 나발 기록을 보내 주세요.")
        Long nabalLossId
) {
}
