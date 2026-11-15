package com.chamabosal.domain.pot.controller;

import com.chamabosal.domain.pot.dto.response.PotCelebrationResponse;
import com.chamabosal.domain.pot.service.PotService;
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
@RequestMapping("/api/rooms")
@Tag(name = "[POT] 한 솥 API", description = "다 채운 솥의 축하 화면")
public class PotController {

    private final PotService potService;

    @GetMapping("/{roomId}/pots/{seq}")
    @Operation(summary = "한 솥 축하 화면 (POT-01)",
            description = """
                    다 채운 솥의 목표 석, 멤버 전원이 받은 보상 석, 멤버별 넣은 석과 순위를 돌려줍니다.
                    seq는 몇 번째 솥인지이고, 보통 오늘 탭의 room.potCompletedCount를 넣습니다.
                    순위에는 그 솥에 넣은 기록이 있는 현재 멤버만 나옵니다. 같은 석이면 같은 순위이고 먼저 넣은 멤버가 앞입니다.
                    """)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공"),
            @ApiResponse(
                    responseCode = "401",
                    description = "COMMON_008 로그인 필요",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "403",
                    description = "ROOM_002 이 방 멤버가 아님",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "ROOM_005 없거나 삭제된 방, POT_001 없거나 아직 다 채우지 않은 솥",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class)
                    )
            )
    })
    public ResponseEntity<Response<PotCelebrationResponse>> getCelebration(
            @Parameter(hidden = true) @AuthenticationPrincipal Long userId,
            @PathVariable Long roomId,
            @PathVariable int seq
    ) {
        return ResponseEntity.ok(Response.ok(potService.getCelebration(userId, roomId, seq)));
    }
}
