package com.chamabosal.domain.auth.client;

import com.chamabosal.global.common.exception.BaseErrorCode;
import com.chamabosal.global.common.exception.BaseException;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.Map;

public class TossApiAuthClient implements TossAuthClient {

    private static final String EXCHANGE_PATH = "/api-partner/v1/apps-in-toss/users/anon-key/exchange";
    private static final String RESULT_SUCCESS = "SUCCESS";
    private static final String INVALID_CODE_ERROR = "4011";

    private final RestClient restClient;

    public TossApiAuthClient(RestClient restClient) {
        this.restClient = restClient;
    }

    /** 비즈니스 오류도 HTTP 200으로 오므로 resultType으로 성공 여부를 판단한다. */
    @Override
    public String exchangeAnonKey(String authorizationCode) {
        ExchangeResponse response = exchange(authorizationCode);
        if (response == null) {
            throw new BaseException(BaseErrorCode.TOSS_API_FAILED);
        }
        if (RESULT_SUCCESS.equals(response.resultType()) && response.hasAnonKey()) {
            return response.success().anonKey();
        }
        if (response.hasErrorCode(INVALID_CODE_ERROR)) {
            throw new BaseException(BaseErrorCode.INVALID_AUTH_CODE);
        }
        throw new BaseException(BaseErrorCode.TOSS_API_FAILED);
    }

    private ExchangeResponse exchange(String authorizationCode) {
        try {
            return restClient.post()
                    .uri(EXCHANGE_PATH)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("code", authorizationCode))
                    .retrieve()
                    .body(ExchangeResponse.class);
        } catch (RestClientException e) {
            throw new BaseException(BaseErrorCode.TOSS_API_FAILED, BaseErrorCode.TOSS_API_FAILED.getMessage(), e);
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record ExchangeResponse(String resultType, Success success, Error error) {

        boolean hasAnonKey() {
            return success != null && success.anonKey() != null && !success.anonKey().isBlank();
        }

        boolean hasErrorCode(String errorCode) {
            return error != null && errorCode.equals(error.errorCode());
        }

        @JsonIgnoreProperties(ignoreUnknown = true)
        record Success(String anonKey) {
        }

        @JsonIgnoreProperties(ignoreUnknown = true)
        record Error(String errorCode) {
        }
    }
}
