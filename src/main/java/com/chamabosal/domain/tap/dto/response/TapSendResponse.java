package com.chamabosal.domain.tap.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

/** 한 솥 적립 결과(potEarned)는 POT-01에서 추가한다. */
public record TapSendResponse(
        @Schema(description = "1번 이상이면 5, 건너뛰면 0. 방 인원이 1명이면 0") int riceEarned
) {
}
