package com.chamabosal.domain.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import com.chamabosal.domain.auth.client.TossAuthClient;
import com.chamabosal.domain.auth.dto.response.AuthLoginResponse;
import com.chamabosal.domain.auth.service.AuthUserService.AuthUser;
import com.chamabosal.global.security.jwt.JwtProvider;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

class AuthServiceTest {

    private final TossAuthClient tossAuthClient = mock(TossAuthClient.class);
    private final AuthUserService authUserService = mock(AuthUserService.class);
    private final JwtProvider jwtProvider = mock(JwtProvider.class);
    private final AuthService authService = new AuthService(tossAuthClient, authUserService, jwtProvider);

    @Test
    void UNIQUE_충돌이_나면_먼저_만들어진_사용자로_로그인한다() {
        given(tossAuthClient.exchangeAnonKey("auth-code")).willReturn("anon-key");
        given(authUserService.findOrCreate("anon-key"))
                .willThrow(new DataIntegrityViolationException("duplicate"))
                .willReturn(new AuthUser(1L, false));
        given(jwtProvider.createAccessToken(1L)).willReturn("token");

        AuthLoginResponse response = authService.login("auth-code");

        assertThat(response.accessToken()).isEqualTo("token");
        assertThat(response.isNewUser()).isFalse();
    }
}
