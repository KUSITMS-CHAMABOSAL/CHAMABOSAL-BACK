package com.chamabosal.domain.auth.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.chamabosal.domain.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {"auth.test-token.enabled=false", "auth.test-token.key=test-key"})
class AuthTestTokenDisabledTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Test
    void AUTH_03_4_기능이_꺼져_있으면_비밀값이_맞아도_COMMON_001을_돌려준다() throws Exception {
        mockMvc.perform(post("/api/auth/test-token")
                        .header("X-Test-Token-Key", "test-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"testUser\":\"a\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("COMMON_001"));

        assertThat(userRepository.count()).isZero();
    }
}
