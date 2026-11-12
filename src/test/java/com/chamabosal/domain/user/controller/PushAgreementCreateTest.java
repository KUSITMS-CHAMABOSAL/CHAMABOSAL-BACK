package com.chamabosal.domain.user.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.chamabosal.domain.user.dto.request.PushAgreementCreateRequest;
import com.chamabosal.domain.user.entity.PushAgreement;
import com.chamabosal.domain.user.entity.PushAgreementType;
import com.chamabosal.domain.user.entity.User;
import com.chamabosal.domain.user.repository.PushAgreementRepository;
import com.chamabosal.domain.user.repository.UserRepository;
import com.chamabosal.domain.user.service.UserService;
import com.chamabosal.global.config.ClockConfig;
import com.chamabosal.global.security.jwt.JwtProvider;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.IntStream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

@SpringBootTest
@AutoConfigureMockMvc
class PushAgreementCreateTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 12, 12, 0);

    @TestConfiguration
    static class FixedClockConfig {

        @Bean
        @Primary
        Clock fixedClock() {
            return Clock.fixed(ZonedDateTime.of(NOW, ClockConfig.KST).toInstant(), ClockConfig.KST);
        }
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserService userService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PushAgreementRepository pushAgreementRepository;

    @Autowired
    private JwtProvider jwtProvider;

    private User user;

    @BeforeEach
    void setUp() {
        user = userRepository.save(User.create("anon-a"));
    }

    @AfterEach
    void tearDown() {
        pushAgreementRepository.deleteAll();
        userRepository.deleteAll();
    }

    /** 내 정보 응답의 동의 여부 확인은 USER-01 구현 때 한다. 이번에는 저장 여부만 확인한다. */
    @Test
    void NOTI_01_1_새로_동의하면_저장한다() throws Exception {
        agree("CERT_REMINDER", "newAgreement")
                .andExpect(status().isOk());

        List<PushAgreement> agreements = pushAgreementRepository.findAll();
        assertThat(agreements).hasSize(1);
        assertThat(agreements.getFirst().getType()).isEqualTo(PushAgreementType.CERT_REMINDER);
        assertThat(agreements.getFirst().getAgreedAt()).isEqualTo(NOW);
    }

    @Test
    void NOTI_01_2_이미_동의했으면_1건이고_처음_동의_시각을_유지한다() throws Exception {
        LocalDateTime firstAgreedAt = NOW.minusDays(3);
        pushAgreementRepository.save(PushAgreement.create(user, PushAgreementType.CERT_REMINDER, firstAgreedAt));

        agree("CERT_REMINDER", "alreadyAgreed")
                .andExpect(status().isOk());

        List<PushAgreement> agreements = pushAgreementRepository.findAll();
        assertThat(agreements).hasSize(1);
        assertThat(agreements.getFirst().getAgreedAt()).isEqualTo(firstAgreedAt);
    }

    @Test
    void NOTI_01_3_거부하면_저장하지_않는다() throws Exception {
        agree("CERT_REMINDER", "agreementRejected")
                .andExpect(status().isOk());

        assertThat(pushAgreementRepository.count()).isZero();
    }

    @Test
    void NOTI_01_4_지원하지_않는_종류면_COMMON_002를_돌려주고_저장하지_않는다() throws Exception {
        agree("POKE", "newAgreement")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON_002"))
                .andExpect(jsonPath("$.field").value("type"));

        assertThat(pushAgreementRepository.count()).isZero();
    }

    @Test
    void NOTI_01_5_동시에_요청해도_동의는_1건만_저장한다() {
        int requestCount = 2;
        ExecutorService executor = Executors.newFixedThreadPool(requestCount);
        try {
            CountDownLatch ready = new CountDownLatch(requestCount);
            CountDownLatch start = new CountDownLatch(1);
            List<CompletableFuture<Void>> futures = IntStream.range(0, requestCount)
                    .mapToObj(i -> CompletableFuture.runAsync(() -> {
                        ready.countDown();
                        await(start);
                        userService.agreePush(user.getId(),
                                new PushAgreementCreateRequest("CERT_REMINDER", "newAgreement"));
                    }, executor))
                    .toList();
            await(ready);
            start.countDown();
            futures.forEach(CompletableFuture::join);

            assertThat(pushAgreementRepository.count()).isEqualTo(1);
        } finally {
            executor.shutdown();
        }
    }

    private ResultActions agree(String type, String result) throws Exception {
        return mockMvc.perform(post("/api/users/me/push-agreements")
                .header("Authorization", "Bearer " + jwtProvider.createAccessToken(user.getId()))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"type\":\"%s\",\"result\":\"%s\"}".formatted(type, result)));
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
