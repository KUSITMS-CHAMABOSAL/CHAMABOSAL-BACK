package com.chamabosal.domain.auth.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.chamabosal.global.common.exception.BaseErrorCode;
import com.chamabosal.global.common.exception.BaseException;
import java.io.IOException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class TossApiAuthClientTest {

    private static final String BASE_URL = "https://toss.test";
    private static final String EXCHANGE_URL = BASE_URL + "/api-partner/v1/apps-in-toss/users/anon-key/exchange";

    private MockRestServiceServer server;
    private TossApiAuthClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl(BASE_URL);
        server = MockRestServiceServer.bindTo(builder).build();
        client = new TossApiAuthClient(builder.build());
    }

    @Test
    void 교환에_성공하면_anonKey를_돌려준다() {
        server.expect(requestTo(EXCHANGE_URL))
                .andExpect(method(HttpMethod.POST))
                .andExpect(jsonPath("$.code").value("auth-code"))
                .andRespond(withSuccess(
                        "{\"resultType\":\"SUCCESS\",\"success\":{\"anonKey\":\"anon-key\"}}",
                        MediaType.APPLICATION_JSON
                ));

        assertThat(client.exchangeAnonKey("auth-code")).isEqualTo("anon-key");
        server.verify();
    }

    @Test
    void 코드_오류_4011은_AUTH_001로_변환한다() {
        respondFail("4011");

        assertFailsWith(BaseErrorCode.INVALID_AUTH_CODE);
    }

    @Test
    void 요청_한도_초과는_AUTH_002로_변환한다() {
        respondFail("4095");

        assertFailsWith(BaseErrorCode.TOSS_API_FAILED);
    }

    @Test
    void SUCCESS가_아닌_resultType은_AUTH_002로_변환한다() {
        server.expect(requestTo(EXCHANGE_URL))
                .andRespond(withSuccess("{\"resultType\":\"HTTP_TIMEOUT\"}", MediaType.APPLICATION_JSON));

        assertFailsWith(BaseErrorCode.TOSS_API_FAILED);
    }

    @Test
    void anonKey가_없는_성공_응답은_AUTH_002로_변환한다() {
        server.expect(requestTo(EXCHANGE_URL))
                .andRespond(withSuccess("{\"resultType\":\"SUCCESS\"}", MediaType.APPLICATION_JSON));

        assertFailsWith(BaseErrorCode.TOSS_API_FAILED);
    }

    @Test
    void HTTP_오류는_AUTH_002로_변환한다() {
        server.expect(requestTo(EXCHANGE_URL)).andRespond(withServerError());

        assertFailsWith(BaseErrorCode.TOSS_API_FAILED);
    }

    @Test
    void 네트워크_오류는_AUTH_002로_변환한다() {
        server.expect(requestTo(EXCHANGE_URL)).andRespond(withException(new IOException("timeout")));

        assertFailsWith(BaseErrorCode.TOSS_API_FAILED);
    }

    private void respondFail(String errorCode) {
        server.expect(requestTo(EXCHANGE_URL))
                .andRespond(withSuccess(
                        "{\"resultType\":\"FAIL\",\"error\":{\"errorCode\":\"" + errorCode + "\",\"reason\":\"실패\"}}",
                        MediaType.APPLICATION_JSON
                ));
    }

    private void assertFailsWith(BaseErrorCode errorCode) {
        assertThatThrownBy(() -> client.exchangeAnonKey("auth-code"))
                .isInstanceOfSatisfying(BaseException.class, e -> assertThat(e.getErrorCode()).isEqualTo(errorCode));
    }
}
