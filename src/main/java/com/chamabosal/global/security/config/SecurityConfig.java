package com.chamabosal.global.security.config;

import com.chamabosal.domain.user.repository.UserRepository;
import com.chamabosal.global.security.filter.JwtAuthenticationFilter;
import com.chamabosal.global.security.handler.SecurityErrorHandler;
import com.chamabosal.global.security.jwt.JwtProvider;
import jakarta.servlet.DispatcherType;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@RequiredArgsConstructor
public class SecurityConfig {

    private static final String[] PUBLIC_PATHS = {
            "/api/auth/login",
            "/api/auth/test-token",
            // 시연 키로 보호한다. 시연 설정이 꺼져 있으면 없는 API처럼 거부한다 (DEMO-01).
            "/api/demo/reset",
            "/actuator/health/**",
            // 수집기(Alloy)가 관리 포트에서 읽는다. 앱 포트에는 노출하지 않는다 (k8s/app/config.env).
            "/actuator/prometheus",
            "/swagger-ui/**",
            "/swagger-ui.html",
            "/v3/api-docs/**",
            "/docs/error-codes",
            // 프론트 공유용 OpenAPI 초안. Swagger 상단 주소창에 넣어 본다.
            "/dev/openapi.yaml"
    };

    private final JwtProvider jwtProvider;
    private final UserRepository userRepository;
    private final SecurityErrorHandler securityErrorHandler;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        return http
                .cors(Customizer.withDefaults())
                .csrf(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()
                        .requestMatchers(PUBLIC_PATHS).permitAll()
                        .anyRequest().authenticated()
                )
                .addFilterBefore(
                        new JwtAuthenticationFilter(jwtProvider, userRepository, securityErrorHandler),
                        UsernamePasswordAuthenticationFilter.class
                )
                .exceptionHandling(exception -> exception
                        .authenticationEntryPoint(securityErrorHandler)
                        .accessDeniedHandler(securityErrorHandler)
                )
                .build();
    }
}
