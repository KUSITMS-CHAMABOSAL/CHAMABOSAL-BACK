package com.chamabosal.domain.certification.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

public record CertificationDeleteResponse(
        @Schema(description = "회수한 개인 공양미. 지급되지 않았거나 이미 삭제한 인증이면 0") int riceRecalled,
        @Schema(description = "한 솥에서 뺀 석. 그 인증이 들어간 솥이 이미 가득 찼으면 0") int potRecalled
) {
}
