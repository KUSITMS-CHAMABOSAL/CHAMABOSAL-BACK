package com.chamabosal.domain.user.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.chamabosal.domain.user.entity.User;
import com.chamabosal.domain.user.repository.UserRepository;
import com.chamabosal.global.security.jwt.JwtProvider;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

@SpringBootTest
@AutoConfigureMockMvc
class UserSettingsUpdateTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JwtProvider jwtProvider;

    private User user;

    @BeforeEach
    void setUp() {
        user = userRepository.save(User.create("anon-a"));
    }

    @AfterEach
    void tearDown() {
        userRepository.deleteAll();
    }

    @Test
    void USER_03_1_보낸_값만_바꾼다() throws Exception {
        update("{\"isPokePushEnabled\":false}")
                .andExpect(status().isOk());

        User updated = userRepository.findById(user.getId()).orElseThrow();
        assertThat(updated.isPokePushEnabled()).isFalse();
        assertThat(updated.isActivityPushEnabled()).isTrue();
    }

    @Test
    void 인증_투표_알림만_바꿀_수_있다() throws Exception {
        user.updatePushSettings(false, null);
        userRepository.save(user);

        update("{\"isActivityPushEnabled\":false}")
                .andExpect(status().isOk());

        User updated = userRepository.findById(user.getId()).orElseThrow();
        assertThat(updated.isActivityPushEnabled()).isFalse();
        assertThat(updated.isPokePushEnabled()).isFalse();
    }

    @Test
    void USER_03_2_값_없이_요청하면_설정이_바뀌지_않는다() throws Exception {
        user.updatePushSettings(false, null);
        userRepository.save(user);

        update("{}")
                .andExpect(status().isOk());

        User updated = userRepository.findById(user.getId()).orElseThrow();
        assertThat(updated.isPokePushEnabled()).isFalse();
        assertThat(updated.isActivityPushEnabled()).isTrue();
    }

    private ResultActions update(String body) throws Exception {
        return mockMvc.perform(patch("/api/users/me/settings")
                .header("Authorization", "Bearer " + jwtProvider.createAccessToken(user.getId()))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }
}
