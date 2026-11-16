package com.chamabosal.domain.certification.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

public record PhotoUploadUrlRequest(
        @Schema(description = "올릴 사진 형식. image/jpeg, image/png, image/heic, image/webp", example = "image/jpeg")
        @NotNull(message = "사진 형식을 입력해 주세요.")
        @Pattern(regexp = "image/(jpeg|png|heic|webp)", message = "jpeg, png, heic, webp 사진만 올릴 수 있습니다.")
        String contentType
) {
}
