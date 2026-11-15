package com.chamabosal.domain.reaction.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

public record ReactionPutResponse(
        @Schema(description = "받은 개인 공양미. 이 인증에 처음 남길 때만 1, 방 인원이 1명이면 0") int riceEarned,
        @Schema(description = "한 솥에 들어간 석. riceEarned와 같음") int potEarned
) {
}
