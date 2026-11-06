package com.chamabosal.domain.auth.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.chamabosal.domain.user.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {"auth.test-token.enabled=true", "auth.test-token.key=" + AuthTestTokenEnabledTest.TEST_KEY})
class AuthTestTokenEnabledTest {

    static final String TEST_KEY = "test-key-with-at-least-32-characters";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @AfterEach
    void tearDown() {
        userRepository.deleteAll();
    }

    @Test
    void AUTH_03_1_비밀값이_맞으면_테스트_사용자를_만들고_토큰을_발급한다() throws Exception {
        mockMvc.perform(request("a").header("X-Test-Token-Key", TEST_KEY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.data.expiresIn").value(3600));

        assertThat(userRepository.findByTossAnonKey("test-a")).isPresent();
    }

    @Test
    void AUTH_03_2_같은_이름으로_다시_요청하면_같은_사용자를_쓴다() throws Exception {
        mockMvc.perform(request("a").header("X-Test-Token-Key", TEST_KEY)).andExpect(status().isOk());
        mockMvc.perform(request("a").header("X-Test-Token-Key", TEST_KEY)).andExpect(status().isOk());

        assertThat(userRepository.count()).isEqualTo(1);
    }

    @Test
    void AUTH_03_3_비밀값이_틀리면_COMMON_008을_돌려준다() throws Exception {
        mockMvc.perform(request("a").header("X-Test-Token-Key", "wrong-key"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("COMMON_008"));

        assertThat(userRepository.count()).isZero();
    }

    @Test
    void AUTH_03_3_비밀값이_없으면_COMMON_008을_돌려준다() throws Exception {
        mockMvc.perform(request("a"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("COMMON_008"));
    }

    @Test
    void 테스트_사용자_이름_형식이_틀리면_COMMON_002를_돌려준다() throws Exception {
        mockMvc.perform(request("TOO-LONG-NAME").header("X-Test-Token-Key", TEST_KEY))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON_002"))
                .andExpect(jsonPath("$.message").value("테스트 사용자 이름은 영문 소문자와 숫자 1~8자입니다."));
    }

    private MockHttpServletRequestBuilder request(String testUser) {
        return post("/api/auth/test-token")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"testUser\":\"" + testUser + "\"}");
    }
}
