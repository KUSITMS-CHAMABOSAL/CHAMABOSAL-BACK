package com.chamabosal.domain.auth.exception;

import com.chamabosal.global.common.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum AuthErrorCode implements ErrorCode {

    INVALID_AUTH_CODE(HttpStatus.BAD_REQUEST, "AUTH_001", "인증 코드가 틀렸거나 만료되었습니다."),
    TOSS_API_FAILED(HttpStatus.BAD_GATEWAY, "AUTH_002", "앱인토스 호출에 실패했습니다.");

    private final HttpStatus httpStatus;
    private final String code;
    private final String message;
}
