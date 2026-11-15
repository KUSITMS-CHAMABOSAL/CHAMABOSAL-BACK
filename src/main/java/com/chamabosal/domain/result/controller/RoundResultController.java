package com.chamabosal.domain.result.controller;

import com.chamabosal.domain.result.dto.response.RoundResultResponse;
import com.chamabosal.domain.result.service.RoundResultService;
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
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api")
@Tag(name = "[RESULT] 결과 API", description = "결과 화면")
public class RoundResultController {

    private final RoundResultService roundResultService;

    @GetMapping("/rounds/{roundId}/result")
    @Operation(summary = "결과 화면 (RESULT-02)",
            description = """
                    발표된 회차의 결과입니다. 누가 누구에게 투표했는지는 보내지 않고, voteCounts는 표를 받은 멤버만 득표 수가 많은 순입니다.
                    지목된 본인이 PICKED 결과를 처음 조회하면 그 시각을 기록하고, defenseUsableUntil은 그 시각 + 2분입니다.
                    지목된 본인에게는 보유한 방어권 개수(defenseTicketCount)와 지금 쓰면 성공하는지(canUseDefense)를 줍니다. 결과를 본 뒤에는 seen-result로 확인을 저장해 주세요.
                    """)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공"),
            @ApiResponse(responseCode = "403", description = "ROOM_002 이 방 멤버가 아님",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "ROUND_001 없는 회차",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "RESULT_001 아직 발표 전",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<Response<RoundResultResponse>> getResult(
            @Parameter(hidden = true) @AuthenticationPrincipal Long userId,
            @PathVariable Long roundId
    ) {
        return ResponseEntity.ok(Response.ok(roundResultService.getResult(userId, roundId)));
    }
}
