package com.chamabosal.domain.auth.client;

import org.springframework.boot.ssl.SslBundles;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.time.Duration;

@Configuration
public class TossAuthClientConfig {

    private static final String BASE_URL = "https://apps-in-toss-api.toss.im";
    private static final String SSL_BUNDLE = "toss";
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(3);
    private static final Duration READ_TIMEOUT = Duration.ofSeconds(5);

    @Bean
    @Profile("prod")
    public TossAuthClient tossApiAuthClient(SslBundles sslBundles) {
        HttpClient httpClient = HttpClient.newBuilder()
                .sslContext(sslBundles.getBundle(SSL_BUNDLE).createSslContext())
                .connectTimeout(CONNECT_TIMEOUT)
                .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(READ_TIMEOUT);
        return new TossApiAuthClient(RestClient.builder()
                .baseUrl(BASE_URL)
                .requestFactory(requestFactory)
                .build());
    }

    @Bean
    @Profile("!prod")
    public TossAuthClient unavailableTossAuthClient() {
        return new UnavailableTossAuthClient();
    }
}
