package com.chamabosal.global.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.micrometer.metrics.test.autoconfigure.AutoConfigureMetrics;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;

/**
 * K8s 모니터링: k8s/app/config.env처럼 관리 포트를 분리하고 prometheus를 노출했을 때, 앱 지표는 관리 포트에서만 토큰 없이 읽히고
 * Gateway가 넘기는 앱 포트에서는 읽히지 않는다. 관리 포트는 테스트마다 비어 있는 포트를 쓴다.
 * 테스트에서는 지표 내보내기가 기본으로 꺼져 있어 {@link AutoConfigureMetrics}로 켠다.
 */
@AutoConfigureMetrics
@SpringBootTest(
        webEnvironment = WebEnvironment.RANDOM_PORT,
        properties = {
                "management.server.port=0",
                "management.endpoints.web.exposure.include=health,prometheus"
        }
)
class PrometheusEndpointConfigTest {

    private final HttpClient httpClient = HttpClient.newHttpClient();

    @Value("${local.server.port}")
    private int serverPort;

    @Value("${local.management.port}")
    private int managementPort;

    @Test
    void 관리_포트에서_토큰_없이_앱_지표를_읽는다() throws Exception {
        HttpResponse<String> response = get(managementPort, "/actuator/prometheus");

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).contains("jvm_memory_used_bytes");
    }

    @Test
    void 앱_포트로_들어온_API_요청이_요청_수와_응답_시간_지표에_쌓인다() throws Exception {
        get(serverPort, "/v3/api-docs");

        assertThat(get(managementPort, "/actuator/prometheus").body())
                .contains("http_server_requests_seconds_count{")
                .contains("uri=\"/v3/api-docs\"");
    }

    @Test
    void 앱_포트에서는_앱_지표를_읽을_수_없다() throws Exception {
        HttpResponse<String> response = get(serverPort, "/actuator/prometheus");

        assertThat(response.statusCode()).isNotEqualTo(200);
        assertThat(response.body()).doesNotContain("jvm_memory_used_bytes");
    }

    @Test
    void 관리_포트에서_토큰_없이_liveness와_readiness가_UP이다() throws Exception {
        assertThat(get(managementPort, "/actuator/health/liveness").body()).contains("\"status\":\"UP\"");
        assertThat(get(managementPort, "/actuator/health/readiness").body()).contains("\"status\":\"UP\"");
    }

    private HttpResponse<String> get(int port, String path) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path)).GET().build();
        return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    }
}
