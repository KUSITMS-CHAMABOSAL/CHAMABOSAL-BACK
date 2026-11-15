package com.chamabosal.domain.certification.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

public record PhotoUploadUrlResponse(
        @Schema(description = "사진을 PUT으로 올릴 주소. Content-Type 헤더는 요청한 contentType과 같아야 함") String uploadUrl,
        @Schema(description = "인증 등록·수정 때 보낼 키. 올린 뒤 1일 동안 쓸 수 있음", example = "tmp/12/2f1c8a7e-...") String imageKey,
        @Schema(description = "uploadUrl 유효 시간(초)", example = "300") long expiresIn
) {
}
