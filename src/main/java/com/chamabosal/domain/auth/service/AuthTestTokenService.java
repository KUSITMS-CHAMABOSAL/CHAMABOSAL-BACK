package com.chamabosal.domain.auth.service;

import com.chamabosal.domain.auth.dto.response.AuthTestTokenResponse;
import com.chamabosal.domain.auth.service.AuthUserService.AuthUser;
import com.chamabosal.global.common.exception.BaseErrorCode;
import com.chamabosal.global.common.exception.BaseException;
import com.chamabosal.global.security.jwt.JwtProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/** 프론트 연동과 API 테스트용 임시 기능이다. 출시 전에 끄고 이후 제거한다. */
@Service
public class AuthTestTokenService {

    private static final String TEST_USER_KEY_PREFIX = "test-";
    private static final int MIN_KEY_LENGTH = 32;
    private static final long EXPIRES_IN_SECONDS = 86400;

    private final AuthService authService;
    private final JwtProvider jwtProvider;
    private final boolean enabled;
    private final byte[] key;

    public AuthTestTokenService(
            AuthService authService,
            JwtProvider jwtProvider,
            @Value("${auth.test-token.enabled}") boolean enabled,
            @Value("${auth.test-token.key}") String key
    ) {
        if (enabled && key.strip().length() < MIN_KEY_LENGTH) {
            throw new IllegalStateException("테스트 토큰을 켜려면 32자 이상의 AUTH_TEST_TOKEN_KEY가 필요합니다.");
        }
        this.authService = authService;
        this.jwtProvider = jwtProvider;
        this.enabled = enabled;
        this.key = key.getBytes(StandardCharsets.UTF_8);
    }

    public AuthTestTokenResponse issue(String requestKey, String testUser) {
        if (!enabled) {
            throw new BaseException(BaseErrorCode.ENDPOINT_NOT_FOUND);
        }
        if (requestKey == null || !MessageDigest.isEqual(key, requestKey.getBytes(StandardCharsets.UTF_8))) {
            throw new BaseException(BaseErrorCode.UNAUTHENTICATED);
        }
        AuthUser user = authService.findOrCreateUser(TEST_USER_KEY_PREFIX + testUser);
        return new AuthTestTokenResponse(jwtProvider.createToken(user.id(), EXPIRES_IN_SECONDS), EXPIRES_IN_SECONDS);
    }
}
