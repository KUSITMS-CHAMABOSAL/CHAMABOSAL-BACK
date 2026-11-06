package com.chamabosal.domain.auth.controller;

import com.chamabosal.domain.auth.dto.request.AuthLoginRequest;
import com.chamabosal.domain.auth.dto.request.AuthTestTokenRequest;
import com.chamabosal.domain.auth.dto.response.AuthLoginResponse;
import com.chamabosal.domain.auth.dto.response.AuthTestTokenResponse;
import com.chamabosal.domain.auth.service.AuthService;
import com.chamabosal.domain.auth.service.AuthTestTokenService;
import com.chamabosal.global.common.response.ErrorResponse;
import com.chamabosal.global.common.response.Response;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/auth")
@Tag(name = "[AUTH] 인증 API", description = "앱인토스 로그인과 토큰 발급 API")
public class AuthController {

    private static final String TEST_TOKEN_KEY_HEADER = "X-Test-Token-Key";

    private final AuthService authService;
    private final AuthTestTokenService authTestTokenService;

    @PostMapping("/login")
    @SecurityRequirements
    @Operation(summary = "로그인", description = "앱인토스 인증 코드로 로그인합니다. 처음 온 사용자는 새로 만듭니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "로그인 성공"),
            @ApiResponse(
                    responseCode = "400",
                    description = "요청 값 오류 또는 잘못된 인증 코드",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "502",
                    description = "앱인토스 호출 실패",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class)
                    )
            )
    })
    public ResponseEntity<Response<AuthLoginResponse>> login(@RequestBody @Valid AuthLoginRequest request) {
        return ResponseEntity.ok(Response.ok(authService.login(request.authorizationCode())));
    }

    @PostMapping("/test-token")
    @SecurityRequirements
    @Operation(summary = "테스트 토큰 발급", description = "개발용 테스트 사용자의 토큰을 발급합니다. 출시 전에 제거합니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "발급 성공"),
            @ApiResponse(
                    responseCode = "401",
                    description = "비밀값 불일치",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "기능 꺼짐",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class)
                    )
            )
    })
    public ResponseEntity<Response<AuthTestTokenResponse>> issueTestToken(
            @RequestHeader(value = TEST_TOKEN_KEY_HEADER, required = false) String key,
            @RequestBody @Valid AuthTestTokenRequest request
    ) {
        return ResponseEntity.ok(Response.ok(authTestTokenService.issue(key, request.testUser())));
    }
}
