package com.chamabosal.domain.tap.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

public record TapSendResponse(
        @Schema(description = "1번 이상이면 5, 건너뛰면 0. 방 인원이 1명이면 0") int riceEarned,
        @Schema(description = "한 솥에 들어간 석. riceEarned와 같음") int potEarned
) {
}
