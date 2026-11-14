package com.chamabosal.domain.vote.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

/** 공양미·한 솥 적립 결과(riceEarned, potEarned)는 RICE-01·POT-01에서 추가한다. */
public record VoteSubmitResponse(
        @Schema(description = "이 표로 투표권 있는 명단 전원이 투표해 마감됐는지") boolean isClosed
) {
}
