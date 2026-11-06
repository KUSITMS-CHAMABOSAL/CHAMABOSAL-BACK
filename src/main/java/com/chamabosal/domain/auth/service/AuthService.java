package com.chamabosal.domain.auth.service;

import com.chamabosal.domain.auth.client.TossAuthClient;
import com.chamabosal.domain.auth.dto.response.AuthLoginResponse;
import com.chamabosal.domain.auth.service.AuthUserService.AuthUser;
import com.chamabosal.global.security.jwt.JwtProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final TossAuthClient tossAuthClient;
    private final AuthUserService authUserService;
    private final JwtProvider jwtProvider;

    /** 외부 API 호출이 DB 트랜잭션을 붙잡지 않도록 사용자 처리만 트랜잭션으로 묶는다. */
    public AuthLoginResponse login(String authorizationCode) {
        String anonKey = tossAuthClient.exchangeAnonKey(authorizationCode);
        AuthUser user = findOrCreateUser(anonKey);
        return new AuthLoginResponse(
                jwtProvider.createAccessToken(user.id()),
                JwtProvider.EXPIRES_IN_SECONDS,
                user.isNew()
        );
    }

    /** 같은 키의 동시 요청이 UNIQUE 제약에 걸리면 먼저 만들어진 사용자를 다시 조회한다. */
    public AuthUser findOrCreateUser(String anonKey) {
        try {
            return authUserService.findOrCreate(anonKey);
        } catch (DataIntegrityViolationException e) {
            return authUserService.findOrCreate(anonKey);
        }
    }
}
