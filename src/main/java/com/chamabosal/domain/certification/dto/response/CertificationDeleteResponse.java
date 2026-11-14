package com.chamabosal.domain.certification.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

/** 한 솥 회수(potRecalled)는 POT-01에서 추가한다. */
public record CertificationDeleteResponse(
        @Schema(description = "회수한 개인 공양미. 지급되지 않았거나 이미 삭제한 인증이면 0") int riceRecalled
) {
}
