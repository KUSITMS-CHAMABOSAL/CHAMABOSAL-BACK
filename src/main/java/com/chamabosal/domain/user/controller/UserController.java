package com.chamabosal.domain.user.controller;

import com.chamabosal.domain.user.dto.request.PushAgreementCreateRequest;
import com.chamabosal.domain.user.dto.request.UserSettingsUpdateRequest;
import com.chamabosal.domain.user.dto.response.UserMeResponse;
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
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/users/me")
@Tag(name = "[USER] 사용자 API", description = "알림 설정, 토스 알림 동의, 인증 캘린더")
public class UserController {

    private final UserService userService;

    @GetMapping
    @Operation(summary = "내 정보 (USER-01)",
            description = """
                    앱을 열 때 불러서 어느 화면으로 갈지 정합니다. rooms가 비어 있으면 첫 화면입니다.
                    방마다 hasUnseen은 안 본 결과나 한 솥 축하가 있는지입니다. is…Agreed는 알림 종류별 토스 동의라 동의 카드를 띄울지 정할 때 씁니다.
                    equippedItemCodes는 곳간 기능 전이라 지금은 항상 빈 배열입니다.
                    """)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공"),
            @ApiResponse(responseCode = "401", description = "COMMON_008 로그인 필요",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<Response<UserMeResponse>> getMe(@Parameter(hidden = true) @AuthenticationPrincipal Long userId) {
        return ResponseEntity.ok(Response.ok(userService.getMe(userId)));
    }

    @PatchMapping("/settings")
    @Operation(summary = "알림 설정 (USER-03)",
            description = """
                    설정 화면의 알림 토글 2개를 바꿉니다. 보낸 값만 바꾸고 빠진 값은 그대로 둡니다. 사용자 단위라 모든 방에 같이 적용됩니다.
                    콕 알림은 콕 찌르기 알림(POKE), 인증·투표 알림은 투표 결과(RESULT)·새 투표(VOTE_OPENED)·인증 마감(CERT_REMINDER) 알림에 적용됩니다.
                    토스 알림 동의(push-agreements)와는 별개입니다. 알림은 그 종류의 토스 동의와 이 토글이 모두 켜져 있어야 보냅니다.
                    """)
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
                    클라이언트가 받은 토스 알림 동의 결과를 저장합니다. 알림 4종은 동의문이 따로 있어 종류마다 보내 주세요. 다른 종류의 동의는 바뀌지 않습니다.
                    newAgreement, alreadyAgreed면 저장하고(이미 있으면 처음 동의 시각 유지), agreementRejected면 저장하지 않습니다.
                    앱 안 알림 토글(settings)과는 별개입니다. 토글을 꺼도 동의는 그대로 남고, 동의를 철회하려면 토스 앱 설정에서 합니다.
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
