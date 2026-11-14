package com.chamabosal.domain.vote.dto.request;

import com.chamabosal.domain.vote.entity.VoteTargetType;
import jakarta.validation.constraints.NotNull;

/** targetType이 MEMBER면 targetMemberId가 필수고, ALL_BOSAL이면 보내지 않는다. 어긋나면 서비스가 COMMON_002로 거부한다. */
public record VoteSubmitRequest(
        @NotNull VoteTargetType targetType,
        Long targetMemberId
) {
}
