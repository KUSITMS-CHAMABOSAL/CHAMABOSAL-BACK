package com.chamabosal.global.common.exception;

import org.springframework.http.HttpStatus;

/**
 * 에러코드 enum이 구현하는 인터페이스. 서비스 에러코드는 BaseErrorCode에 도메인 구역별로 모은다.
 * code는 DOMAIN_nnn 형식으로 작성한다. (예: COMMON_001, ROOM_001)
 */
public interface ErrorCode {

    HttpStatus getHttpStatus();

    String getCode();

    String getMessage();
}
