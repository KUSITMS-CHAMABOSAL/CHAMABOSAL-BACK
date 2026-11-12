package com.chamabosal.global.common.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum BaseErrorCode implements ErrorCode {

    // Common
    ENDPOINT_NOT_FOUND(HttpStatus.NOT_FOUND, "COMMON_001", "요청하신 리소스를 찾을 수 없습니다."),
    INVALID_REQUEST(HttpStatus.BAD_REQUEST, "COMMON_002", "요청 값이 올바르지 않습니다."),
    INVALID_JSON_REQUEST(HttpStatus.BAD_REQUEST, "COMMON_003", "요청 본문을 읽을 수 없습니다."),
    MISSING_PARAMETER(HttpStatus.BAD_REQUEST, "COMMON_004", "필수 요청 파라미터가 누락되었습니다."),
    TYPE_MISMATCH(HttpStatus.BAD_REQUEST, "COMMON_005", "요청 값의 타입이 올바르지 않습니다."),
    METHOD_NOT_ALLOWED(HttpStatus.METHOD_NOT_ALLOWED, "COMMON_006", "지원하지 않는 HTTP 메서드입니다."),
    UNSUPPORTED_MEDIA_TYPE(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "COMMON_007", "지원하지 않는 Content-Type 입니다."),
    UNAUTHENTICATED(HttpStatus.UNAUTHORIZED, "COMMON_008", "인증이 필요합니다."),
    FORBIDDEN(HttpStatus.FORBIDDEN, "COMMON_009", "접근 권한이 없습니다."),
    UNPROCESSABLE_REQUEST(HttpStatus.BAD_REQUEST, "COMMON_010", "처리할 수 없는 요청입니다."),

    UNHANDLED_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "COMMON_999", "서버 내부 오류가 발생했습니다."),

    // Auth
    INVALID_AUTH_CODE(HttpStatus.BAD_REQUEST, "AUTH_001", "인증 코드가 틀렸거나 만료되었습니다."),
    TOSS_API_FAILED(HttpStatus.BAD_GATEWAY, "AUTH_002", "앱인토스 호출에 실패했습니다."),

    // Room
    ROOM_LIMIT_EXCEEDED(HttpStatus.CONFLICT, "ROOM_001", "참여 중인 방이 2개입니다."),
    NOT_ROOM_MEMBER(HttpStatus.FORBIDDEN, "ROOM_002", "이 방의 멤버가 아닙니다."),
    ROOM_FULL(HttpStatus.CONFLICT, "ROOM_003", "방이 가득 찼습니다."),
    NICKNAME_DUPLICATED(HttpStatus.CONFLICT, "ROOM_004", "이미 쓰는 닉네임입니다."),
    ROOM_NOT_FOUND(HttpStatus.NOT_FOUND, "ROOM_005", "없거나 삭제된 방입니다."),
    ROOM_UNAVAILABLE(HttpStatus.FORBIDDEN, "ROOM_006", "들어갈 수 없는 방입니다."),

    // 새 도메인 구역은 이 줄 위에 추가한다. 마지막 상수도 쉼표로 끝내 추가할 때 다른 줄을 건드리지 않는다.
    ;

    private final HttpStatus httpStatus;
    private final String code;
    private final String message;
}
