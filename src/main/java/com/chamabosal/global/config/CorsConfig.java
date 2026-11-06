package com.chamabosal.global.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

/** Security 필터가 MVC보다 먼저 실행되므로 http.cors()가 읽는 CorsConfigurationSource로 등록한다. */
@Configuration
public class CorsConfig {

    private static final List<String> ALLOWED_ORIGINS = List.of(
            // 앱인토스 미니앱 (정식 서비스, 콘솔 QR 테스트)
            "https://chamabosal.apps.tossmini.com",
            "https://chamabosal.private-apps.tossmini.com",
            "https://chamabosal.web.tossmini.com",
            "https://chamabosal.private-web.tossmini.com",
            // 프론트 로컬 개발 서버
            "http://localhost:5173"
    );
    private static final long PREFLIGHT_MAX_AGE_SECONDS = 1800;

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(ALLOWED_ORIGINS);
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("*"));
        configuration.setMaxAge(PREFLIGHT_MAX_AGE_SECONDS);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}
