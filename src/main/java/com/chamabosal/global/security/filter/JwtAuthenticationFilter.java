package com.chamabosal.global.security.filter;

import com.chamabosal.domain.user.repository.UserRepository;
import com.chamabosal.global.security.handler.SecurityErrorHandler;
import com.chamabosal.global.security.jwt.JwtProvider;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/** 토큰이 유효하지 않으면 인증 없이 넘기고, 보호된 API는 Security가 COMMON_008로 거절한다. */
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtProvider jwtProvider;
    private final UserRepository userRepository;
    private final SecurityErrorHandler securityErrorHandler;

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        try {
            authenticate(request);
        } catch (DataAccessException e) {
            // 필터 예외는 GlobalExceptionHandler를 거치지 않아 응답 형식과 장애 알림이 빠진다.
            securityErrorHandler.resolve(request, response, e);
            return;
        }
        filterChain.doFilter(request, response);
    }

    private void authenticate(HttpServletRequest request) {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header != null && header.startsWith(BEARER_PREFIX)) {
            jwtProvider.parseUserId(header.substring(BEARER_PREFIX.length()))
                    .filter(userRepository::existsByIdAndDeletedAtIsNull)
                    .ifPresent(this::authenticate);
        }
    }

    private void authenticate(Long userId) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(userId, null, List.of())
        );
    }
}
