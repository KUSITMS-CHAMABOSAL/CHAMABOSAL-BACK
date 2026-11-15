package com.chamabosal.domain.nabal.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

public record NabalReplantResponse(
        @Schema(description = "심은 뒤 공양미 표시 잔액") int riceBalance,
        @Schema(description = "심은 뒤 남은 빠진 나발 번호, 오름차순. 비어 있으면 보살로 돌아옴") List<Integer> fallenNumbers
) {
}
