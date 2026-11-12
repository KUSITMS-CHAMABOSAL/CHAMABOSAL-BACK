package com.chamabosal.domain.user.controller;

import com.chamabosal.domain.user.dto.request.PushAgreementCreateRequest;
import com.chamabosal.domain.user.dto.request.UserSettingsUpdateRequest;
import com.chamabosal.domain.user.service.UserService;
import com.chamabosal.global.common.response.ErrorResponse;
import com.chamabosal.global.common.response.Response;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/users/me")
@Tag(name = "[USER] 사용자 API", description = "알림 설정, 토스 알림 동의")
public class UserController {

    private final UserService userService;

    @PatchMapping("/settings")
    @Operation(summary = "알림 설정 (USER-03)",
            description = "앱 안의 콕 알림, 인증·투표 알림 설정을 바꿉니다. 보낸 값만 바꾸고 빠진 값은 그대로 둡니다. 토스 알림 동의와는 별개입니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "변경 성공"),
            @ApiResponse(
                    responseCode = "400",
                    description = "COMMON_002 입력값 오류",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "COMMON_008 로그인 필요",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class)
                    )
            )
    })
    public ResponseEntity<Response<Void>> updateSettings(
            @Parameter(hidden = true) @AuthenticationPrincipal Long userId,
            @RequestBody @Valid UserSettingsUpdateRequest request
    ) {
        userService.updateSettings(userId, request);
        return ResponseEntity.ok(Response.ok(null));
    }

    @PostMapping("/push-agreements")
    @Operation(summary = "토스 알림 동의 (NOTI-01)",
            description = """
                    클라이언트가 받은 토스 알림 동의 결과를 저장합니다. 지금은 CERT_REMINDER만 받습니다.
                    newAgreement, alreadyAgreed면 저장하고(이미 있으면 처음 동의 시각 유지), agreementRejected면 저장하지 않습니다.
                    저장 여부와 상관없이 200을 돌려줍니다.
                    """)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "처리 성공"),
            @ApiResponse(
                    responseCode = "400",
                    description = "COMMON_002 입력값 오류 (field: type, result)",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "COMMON_008 로그인 필요",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class)
                    )
            )
    })
    public ResponseEntity<Response<Void>> agreePush(
            @Parameter(hidden = true) @AuthenticationPrincipal Long userId,
            @RequestBody @Valid PushAgreementCreateRequest request
    ) {
        userService.agreePush(userId, request);
        return ResponseEntity.ok(Response.ok(null));
    }
}
