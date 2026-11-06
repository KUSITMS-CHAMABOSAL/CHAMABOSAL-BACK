package com.chamabosal.domain.auth.dto.request;

import jakarta.validation.constraints.NotBlank;

public record AuthLoginRequest(
        @NotBlank(message = "인증 코드를 입력해 주세요.") String authorizationCode
) {
}
