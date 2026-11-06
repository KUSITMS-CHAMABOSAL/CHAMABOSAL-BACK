package com.chamabosal.global.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Spring Security 도입 시 필터가 MVC보다 먼저 실행되어 preflight가 막히므로 http.cors()로 옮긴다.
 */
@Configuration
public class CorsConfig implements WebMvcConfigurer {

    private static final String[] ALLOWED_ORIGINS = {
            // 앱인토스 미니앱 (정식 서비스, 콘솔 QR 테스트)
            "https://chamabosal.apps.tossmini.com",
            "https://chamabosal.private-apps.tossmini.com",
            "https://chamabosal.web.tossmini.com",
            "https://chamabosal.private-web.tossmini.com",
            // 프론트 로컬 개발 서버
            "http://localhost:5173"
    };

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**")
                .allowedOrigins(ALLOWED_ORIGINS)
                .allowedMethods("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
                .allowedHeaders("*");
    }
}
