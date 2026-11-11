package com.chamabosal.global.common.exception;

import lombok.Getter;

@Getter
public class BaseException extends RuntimeException {

    private final ErrorCode errorCode;
    private final String field;

    public BaseException(ErrorCode errorCode) {
        this(errorCode, errorCode.getMessage(), null, null);
    }

    public BaseException(ErrorCode errorCode, String message) {
        this(errorCode, message, null, null);
    }

    public BaseException(ErrorCode errorCode, String message, Throwable cause) {
        this(errorCode, message, null, cause);
    }

    private BaseException(ErrorCode errorCode, String message, String field, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
        this.field = field;
    }

    public static BaseException withField(ErrorCode errorCode, String field) {
        return withField(errorCode, errorCode.getMessage(), field);
    }

    /** 인자 순서는 ErrorResponse.of(errorCode, message, field)와 같다. */
    public static BaseException withField(ErrorCode errorCode, String message, String field) {
        return new BaseException(errorCode, message, field, null);
    }
}
