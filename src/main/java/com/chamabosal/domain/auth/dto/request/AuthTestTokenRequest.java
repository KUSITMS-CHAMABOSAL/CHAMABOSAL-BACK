package com.chamabosal.domain.auth.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

public record AuthTestTokenRequest(
        @NotNull(message = "테스트 사용자 이름을 입력해 주세요.")
        @Pattern(regexp = "[a-z0-9]{1,8}", message = "테스트 사용자 이름은 영문 소문자와 숫자 1~8자입니다.")
        String testUser
) {
}
