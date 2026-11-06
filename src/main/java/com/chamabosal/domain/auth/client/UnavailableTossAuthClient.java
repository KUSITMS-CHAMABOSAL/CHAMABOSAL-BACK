package com.chamabosal.domain.auth.client;

import com.chamabosal.domain.auth.exception.AuthErrorCode;
import com.chamabosal.global.common.exception.BaseException;

/** mTLS 인증서가 없는 로컬에서 쓴다. 로컬 로그인은 테스트 토큰으로 대신한다. */
public class UnavailableTossAuthClient implements TossAuthClient {

    @Override
    public String exchangeAnonKey(String authorizationCode) {
        throw new BaseException(AuthErrorCode.TOSS_API_FAILED, "이 환경에서는 앱인토스 로그인을 사용할 수 없습니다.");
    }
}
