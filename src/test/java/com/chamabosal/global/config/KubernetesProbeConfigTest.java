package com.chamabosal.global.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.context.LifecycleProperties;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.web.server.Shutdown;
import org.springframework.boot.web.server.autoconfigure.ServerProperties;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

/**
 * K8s 전환: liveness·readiness probe 경로와 graceful shutdown. 둘 다 Spring Boot 4 기본값이라 설정 없이 켜져 있고,
 * 매니페스트의 probe 경로와 terminationGracePeriodSeconds가 이 값에 기대므로 바뀌지 않게 지킨다.
 */
@SpringBootTest
@AutoConfigureMockMvc
class KubernetesProbeConfigTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ServerProperties serverProperties;

    @Autowired
    private LifecycleProperties lifecycleProperties;

    @Test
    void liveness_probe는_토큰_없이_UP을_돌려준다() throws Exception {
        mockMvc.perform(get("/actuator/health/liveness"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }

    @Test
    void readiness_probe는_토큰_없이_UP을_돌려준다() throws Exception {
        mockMvc.perform(get("/actuator/health/readiness"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }

    @Test
    void 종료할_때_처리_중인_요청을_마치고_끈다() {
        assertThat(serverProperties.getShutdown()).isEqualTo(Shutdown.GRACEFUL);
        assertThat(lifecycleProperties.getTimeoutPerShutdownPhase()).isEqualTo(Duration.ofSeconds(30));
    }
}
