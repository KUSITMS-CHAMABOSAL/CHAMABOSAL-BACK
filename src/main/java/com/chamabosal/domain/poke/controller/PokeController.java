package com.chamabosal.domain.poke.controller;

import com.chamabosal.domain.poke.dto.request.PokeSendRequest;
import com.chamabosal.domain.poke.service.PokeService;
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
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/rooms")
@Tag(name = "[POKE] 콕 API", description = "같은 방 멤버 콕 찌르기")
public class PokeController {

    private final PokeService pokeService;

    @PostMapping("/{roomId}/pokes")
    @Operation(summary = "콕 찌르기 (POKE-01)",
            description = """
                    인증 시간에 아직 인증하지 않은 같은 방 멤버를 콕 찌릅니다. 오늘 회차에 쌓입니다. 투표 중이거나 결과 뒤 다음 회차 인증 전이면 POKE_002, 받는 사람이 이미 인증했으면 POKE_003입니다.
                    연타는 화면에서 1~2초 모아 count(1~30)로 보내면 서버가 더합니다. 콕 푸시는 아직 보내지 않습니다(POKE-02).
                    확인 순서: COMMON_002 → ROOM_005 → ROOM_002 → POKE_001 → ROOM_009 → POKE_002 → POKE_003
                    """)
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "저장 성공"),
            @ApiResponse(
                    responseCode = "400",
                    description = "COMMON_002 입력값 오류 (field: toMemberId, count) / POKE_001 나를 찌름",
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
                    description = "ROOM_005 없거나 삭제된 방 / ROOM_009 받는 사람이 이 방 현재 멤버가 아님",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "409",
                    description = "POKE_002 인증 시간이 아님 / POKE_003 받는 사람이 이미 인증함",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class)
                    )
            )
    })
    public ResponseEntity<Response<Void>> send(
            @Parameter(hidden = true) @AuthenticationPrincipal Long userId,
            @PathVariable Long roomId,
            @RequestBody @Valid PokeSendRequest request
    ) {
        pokeService.send(userId, roomId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(Response.created("찔렀어요.", null));
    }
}
