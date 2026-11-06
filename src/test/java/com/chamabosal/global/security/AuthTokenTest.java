package com.chamabosal.global.security;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.chamabosal.domain.user.entity.User;
import com.chamabosal.domain.user.repository.UserRepository;
import com.chamabosal.global.security.jwt.JwtProvider;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@SpringBootTest
@AutoConfigureMockMvc
@Import(AuthTokenTest.ProtectedController.class)
class AuthTokenTest {

    private static final String PROTECTED_PATH = "/test/protected";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JwtProvider jwtProvider;

    @Autowired
    private Clock clock;

    @Value("${jwt.secret}")
    private String secret;

    @AfterEach
    void tearDown() {
        userRepository.deleteAll();
    }

    @Test
    void AUTH_02_1_유효한_토큰이면_요청을_처리한다() throws Exception {
        User user = userRepository.save(User.create("anon-key"));

        requestWith(jwtProvider.createAccessToken(user.getId()))
                .andExpect(status().isOk())
                .andExpect(content().string("ok"));
    }

    @Test
    void AUTH_02_2_토큰이_없으면_COMMON_008을_돌려준다() throws Exception {
        assertUnauthenticated(mockMvc.perform(get(PROTECTED_PATH)));
    }

    @Test
    void AUTH_02_3_만료된_토큰이면_COMMON_008을_돌려준다() throws Exception {
        User user = userRepository.save(User.create("anon-key"));
        Clock expiredClock = Clock.offset(clock, Duration.ofSeconds(-JwtProvider.EXPIRES_IN_SECONDS - 1));
        String expiredToken = new JwtProvider(secret, expiredClock).createAccessToken(user.getId());

        assertUnauthenticated(requestWith(expiredToken));
    }

    @Test
    void AUTH_02_4_삭제한_사용자의_토큰이면_COMMON_008을_돌려준다() throws Exception {
        User user = User.create("anon-key");
        ReflectionTestUtils.setField(user, "deletedAt", LocalDateTime.now(clock));
        userRepository.save(user);

        assertUnauthenticated(requestWith(jwtProvider.createAccessToken(user.getId())));
    }

    @Test
    void 위조된_토큰이면_COMMON_008을_돌려준다() throws Exception {
        assertUnauthenticated(requestWith("forged.token.value"));
    }

    @Test
    void 헬스체크는_토큰_없이_호출할_수_있다() throws Exception {
        mockMvc.perform(get("/actuator/health")).andExpect(status().isOk());
    }

    @Test
    void 에러코드_문서는_토큰_없이_볼_수_있다() throws Exception {
        mockMvc.perform(get("/docs/error-codes")).andExpect(status().isOk());
    }

    @Test
    void 로그인_경로를_잘못된_메서드로_호출하면_COMMON_006을_돌려준다() throws Exception {
        mockMvc.perform(get("/api/auth/login"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.code").value("COMMON_006"));
    }

    private ResultActions requestWith(String token) throws Exception {
        return mockMvc.perform(get(PROTECTED_PATH).header(HttpHeaders.AUTHORIZATION, "Bearer " + token));
    }

    private void assertUnauthenticated(ResultActions result) throws Exception {
        result.andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("COMMON_008"));
    }

    @RestController
    static class ProtectedController {

        @GetMapping(PROTECTED_PATH)
        String get() {
            return "ok";
        }
    }
}
