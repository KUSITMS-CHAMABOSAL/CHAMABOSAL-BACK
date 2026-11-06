package com.chamabosal.domain.auth.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.chamabosal.domain.auth.client.TossAuthClient;
import com.chamabosal.domain.auth.exception.AuthErrorCode;
import com.chamabosal.domain.auth.service.AuthService;
import com.chamabosal.domain.user.entity.User;
import com.chamabosal.domain.user.repository.UserRepository;
import com.chamabosal.global.common.exception.BaseException;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

@SpringBootTest
@AutoConfigureMockMvc
class AuthLoginTest {

    private static final String ANON_KEY = "anon-key";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private AuthService authService;

    @Autowired
    private Clock clock;

    @MockitoBean
    private TossAuthClient tossAuthClient;

    @BeforeEach
    void setUp() {
        given(tossAuthClient.exchangeAnonKey(anyString())).willReturn(ANON_KEY);
    }

    @AfterEach
    void tearDown() {
        userRepository.deleteAll();
    }

    @Test
    void AUTH_01_1_처음_온_사용자를_만들고_토큰을_돌려준다() throws Exception {
        login()
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.data.expiresIn").value(3600))
                .andExpect(jsonPath("$.data.isNewUser").value(true));

        assertThat(userRepository.findByTossAnonKey(ANON_KEY)).isPresent();
    }

    @Test
    void AUTH_01_2_기존_사용자는_새로_만들지_않는다() throws Exception {
        userRepository.save(User.create(ANON_KEY));

        login()
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.isNewUser").value(false));

        assertThat(userRepository.count()).isEqualTo(1);
    }

    @Test
    void AUTH_01_3_6개월_안에_돌아온_사용자를_복구한다() throws Exception {
        User user = deletedUser(LocalDateTime.now(clock).minusMonths(6).plusMinutes(1));

        login()
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.isNewUser").value(false));

        User restored = userRepository.findById(user.getId()).orElseThrow();
        assertThat(restored.getDeletedAt()).isNull();
        assertThat(userRepository.count()).isEqualTo(1);
    }

    @Test
    void AUTH_01_4_6개월이_지난_사용자는_신규로_처리한다() throws Exception {
        User user = deletedUser(LocalDateTime.now(clock).minusMonths(6).minusMinutes(1));

        login()
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.isNewUser").value(true));

        assertThat(userRepository.findById(user.getId()).orElseThrow().getTossAnonKey()).isNull();
        assertThat(userRepository.findByTossAnonKey(ANON_KEY).orElseThrow().getId()).isNotEqualTo(user.getId());
    }

    @Test
    void AUTH_01_5_잘못된_인증_코드는_AUTH_001을_돌려준다() throws Exception {
        given(tossAuthClient.exchangeAnonKey(anyString()))
                .willThrow(new BaseException(AuthErrorCode.INVALID_AUTH_CODE));

        login()
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("AUTH_001"));

        assertThat(userRepository.count()).isZero();
    }

    @Test
    void AUTH_01_6_앱인토스_호출_실패는_AUTH_002를_돌려준다() throws Exception {
        given(tossAuthClient.exchangeAnonKey(anyString()))
                .willThrow(new BaseException(AuthErrorCode.TOSS_API_FAILED));

        login()
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.code").value("AUTH_002"));

        assertThat(userRepository.count()).isZero();
    }

    @Test
    void AUTH_01_7_동시_첫_로그인에도_사용자는_1명만_만든다() {
        int rounds = 20;
        int requestCount = 8;

        try (ExecutorService executor = Executors.newFixedThreadPool(requestCount)) {
            for (int round = 0; round < rounds; round++) {
                given(tossAuthClient.exchangeAnonKey(anyString())).willReturn(ANON_KEY + round);

                assertThat(loginConcurrently(executor, requestCount).filter(isNew -> isNew)).hasSize(1);
            }
        }
        assertThat(userRepository.count()).isEqualTo(rounds);
    }

    @Test
    void 인증_코드가_없으면_COMMON_002를_돌려준다() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"authorizationCode\":\" \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON_002"));
    }

    private ResultActions login() throws Exception {
        return mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"authorizationCode\":\"auth-code\"}"));
    }

    private User deletedUser(LocalDateTime deletedAt) {
        User user = User.create(ANON_KEY);
        ReflectionTestUtils.setField(user, "deletedAt", deletedAt);
        return userRepository.save(user);
    }

    private Stream<Boolean> loginConcurrently(ExecutorService executor, int requestCount) {
        CountDownLatch ready = new CountDownLatch(requestCount);
        CountDownLatch start = new CountDownLatch(1);
        List<CompletableFuture<Boolean>> results = IntStream.range(0, requestCount)
                .mapToObj(i -> CompletableFuture.supplyAsync(() -> {
                    ready.countDown();
                    await(start);
                    return authService.login("auth-code").isNewUser();
                }, executor))
                .toList();

        await(ready);
        start.countDown();
        return results.stream().map(CompletableFuture::join);
    }

    private void await(CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }
}
