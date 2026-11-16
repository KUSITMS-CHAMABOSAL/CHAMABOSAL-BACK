package com.chamabosal.global.common.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import org.springframework.http.HttpStatus;

public record Response<T>(
        boolean success,
        String code,
        String message,
        @JsonInclude(JsonInclude.Include.NON_NULL) T data
) {
    private static final String DEFAULT_MESSAGE = "요청이 성공했어요.";

    public static <T> Response<T> ok(T data) {
        return of(HttpStatus.OK, DEFAULT_MESSAGE, data);
    }

    public static <T> Response<T> ok(String message, T data) {
        return of(HttpStatus.OK, message, data);
    }

    public static <T> Response<T> created(String message, T data) {
        return of(HttpStatus.CREATED, message, data);
    }

    private static <T> Response<T> of(HttpStatus httpStatus, String message, T data) {
        return new Response<>(true, String.valueOf(httpStatus.value()), message, data);
    }
}
