package com.chamabosal.domain.defense.controller;

import com.chamabosal.domain.defense.dto.response.DefenseTicketPurchaseResponse;
import com.chamabosal.domain.defense.service.DefenseService;
import com.chamabosal.global.common.response.ErrorResponse;
import com.chamabosal.global.common.response.Response;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api")
@Tag(name = "[DEFENSE] 이마 방어권 API", description = "방어권 구매·사용")
public class DefenseController {

    private final DefenseService defenseService;

    @PostMapping("/defense-tickets")
    @Operation(summary = "방어권 구매 (SHIELD-01)",
            description = "공양미 100석으로 이마 방어권을 삽니다. 보유 개수 제한이 없습니다. 응답은 산 뒤의 표시 잔액입니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "구매 성공"),
            @ApiResponse(responseCode = "409", description = "STORE_001 공양미 부족",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<Response<DefenseTicketPurchaseResponse>> purchase(
            @Parameter(hidden = true) @AuthenticationPrincipal Long userId
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(Response.created("방어권을 샀습니다.", defenseService.purchase(userId)));
    }

    @PostMapping("/rounds/{roundId}/defenses")
    @Operation(summary = "방어권 사용 (SHIELD-02)",
            description = """
                    지목된 사람이 결과를 처음 본 때부터 2분 안에 그 회차에 방어권을 씁니다. 쓰는 순간 하나 소모되고 결과가 DEFENDED로 바뀌며,
                    그 회차 톡톡은 무효가 됩니다. 결과를 보지 않고 바로 쓰면 이 요청 시각을 처음 조회 시각으로 기록합니다.
                    이미 방어한 본인이 다시 보내면 방어권을 더 쓰지 않고 성공합니다.
                    결과를 열지 않았으면 다음 투표가 열릴 때까지 쓸 수 있습니다(Q-77 임시 결정).
                    확인 순서: ROUND_001 → ROOM_002 → RESULT_001 → 이미 방어함(성공) → DEFENSE_008 → DEFENSE_009 → DEFENSE_002
                    """)
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "사용 성공"),
            @ApiResponse(responseCode = "403", description = "ROOM_002 이 방 멤버가 아님 / DEFENSE_008 지목된 사람 아님",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "ROUND_001 없는 회차",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "RESULT_001 발표 전 / DEFENSE_009 방어 시간 지남 / DEFENSE_002 방어권 없음",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<Response<Void>> use(
            @Parameter(hidden = true) @AuthenticationPrincipal Long userId,
            @PathVariable Long roundId
    ) {
        defenseService.use(userId, roundId);
        return ResponseEntity.status(HttpStatus.CREATED).body(Response.created("방어권을 썼습니다.", null));
    }
}
