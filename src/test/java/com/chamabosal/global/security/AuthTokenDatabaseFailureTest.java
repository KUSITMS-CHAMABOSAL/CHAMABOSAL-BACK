package com.chamabosal.global.security;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.chamabosal.domain.user.repository.UserRepository;
import com.chamabosal.global.discord.ServerErrorEvent;
import com.chamabosal.global.security.jwt.JwtProvider;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@RecordApplicationEvents
class AuthTokenDatabaseFailureTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtProvider jwtProvider;

    @Autowired
    private ApplicationEvents events;

    @MockitoBean
    private UserRepository userRepository;

    @Test
    void 토큰_확인_중_DB_장애가_나면_공통_에러로_응답하고_알림을_보낸다() throws Exception {
        given(userRepository.existsByIdAndDeletedAtIsNull(anyLong()))
                .willThrow(new DataAccessResourceFailureException("DB 연결 실패"));

        mockMvc.perform(get("/test/protected")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + jwtProvider.createAccessToken(1L)))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("COMMON_999"));

        Assertions.assertThat(events.stream(ServerErrorEvent.class)).hasSize(1);
    }
}
