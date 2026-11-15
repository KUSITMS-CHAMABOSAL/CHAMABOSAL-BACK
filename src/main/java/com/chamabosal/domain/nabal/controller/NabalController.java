package com.chamabosal.domain.nabal.controller;

import com.chamabosal.domain.nabal.dto.request.NabalReplantRequest;
import com.chamabosal.domain.nabal.dto.request.NabalSeenUpdateRequest;
import com.chamabosal.domain.nabal.dto.response.NabalReplantResponse;
import com.chamabosal.domain.nabal.service.NabalService;
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
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/users/me")
@Tag(name = "[NABAL] 나발 API", description = "나발 빠짐 확인, 나발 심기. 나발 상태는 내 정보(GET /api/users/me)의 nabal")
public class NabalController {

    private final NabalService nabalService;

    @PutMapping("/seen-nabal-loss")
    @Operation(summary = "나발 빠짐 확인 (NABAL-02)",
            description = """
                    빠지는 연출을 본 뒤 내 정보의 nabal.lastNabalLossId를 보내 주세요. 그 기록과 이전 기록을 모두 본 것으로 처리합니다.
                    화면을 그린 뒤 새로 빠진 나발은 다음 앱 진입 때 다시 unseenFallenNumbers로 옵니다.
                    """)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "저장 성공"),
            @ApiResponse(responseCode = "400", description = "COMMON_002 기록 ID 없음 (field: nabalLossId)",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "COMMON_008 로그인 필요",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<Response<Void>> markSeen(
            @Parameter(hidden = true) @AuthenticationPrincipal Long userId,
            @RequestBody @Valid NabalSeenUpdateRequest request
    ) {
        nabalService.markSeen(userId, request);
        return ResponseEntity.ok(Response.ok(null));
    }

    @PostMapping("/nabal-replants")
    @Operation(summary = "나발 심기 (NABAL-03)",
            description = """
                    화면에서 고른 빠진 나발을 개당 15석으로 다시 심습니다. 여러 개를 한 번에 보낼 수 있습니다.
                    하나라도 빠지지 않은 나발이 있으면 아무것도 심지 않고 NABAL_001입니다. 빠진 나발을 모두 심으면 보살로 돌아옵니다.
                    확인 순서: COMMON_002(빈 목록·범위·중복) → NABAL_001 → STORE_001
                    """)
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "심기 성공"),
            @ApiResponse(responseCode = "400", description = "COMMON_002 빈 목록, 0~37 밖, 같은 번호 중복 (field: nabalNumbers)",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "COMMON_008 로그인 필요",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "NABAL_001 빠지지 않은 나발 / STORE_001 공양미 부족",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<Response<NabalReplantResponse>> replant(
            @Parameter(hidden = true) @AuthenticationPrincipal Long userId,
            @RequestBody @Valid NabalReplantRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED).body(Response.ok(nabalService.replant(userId, request)));
    }
}
