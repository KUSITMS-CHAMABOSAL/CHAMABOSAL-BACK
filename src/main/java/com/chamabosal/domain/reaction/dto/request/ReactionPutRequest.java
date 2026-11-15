package com.chamabosal.domain.reaction.dto.request;

import com.chamabosal.domain.reaction.entity.ReactionType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

public record ReactionPutRequest(
        @Schema(description = "GOOD(잘 참았어요) 또는 BAD(아쉬워요)", example = "GOOD")
        @NotNull(message = "리액션 종류를 입력해 주세요.")
        ReactionType type
) {
}
