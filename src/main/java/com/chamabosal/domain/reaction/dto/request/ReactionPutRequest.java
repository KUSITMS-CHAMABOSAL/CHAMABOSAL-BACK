package com.chamabosal.domain.reaction.dto.request;

import com.chamabosal.domain.reaction.entity.ReactionType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

/** enum으로 받으면 없는 종류가 JSON 변환 오류(COMMON_003)가 되어, 문자열로 받고 COMMON_002로 검사한다 (PushAgreementCreateRequest와 같은 이유). */
public record ReactionPutRequest(
        @Schema(description = "GOOD(잘 참았어요) 또는 BAD(아쉬워요)", allowableValues = {"GOOD", "BAD"}, example = "GOOD")
        @NotNull(message = "리액션 종류를 입력해 주세요.")
        @Pattern(regexp = "GOOD|BAD", message = "리액션 종류는 GOOD 또는 BAD예요.")
        String type
) {

    public ReactionType reactionType() {
        return ReactionType.valueOf(type);
    }
}
