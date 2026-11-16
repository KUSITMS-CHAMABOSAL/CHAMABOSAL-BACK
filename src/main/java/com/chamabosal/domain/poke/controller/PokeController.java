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
                    같은 방 멤버를 콕 찌릅니다. 인증 여부와 회차 상태에 상관없이 언제나 보낼 수 있고, 오늘 회차에 쌓입니다.
                    연타는 화면에서 1~2초 모아 count(1~30)로 보내면 서버가 더합니다. 콕 푸시는 아직 보내지 않습니다(POKE-02).
                    확인 순서: COMMON_002 → ROOM_005 → ROOM_002 → POKE_001 → ROOM_009
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
            )
    })
    public ResponseEntity<Response<Void>> send(
            @Parameter(hidden = true) @AuthenticationPrincipal Long userId,
            @PathVariable Long roomId,
            @RequestBody @Valid PokeSendRequest request
    ) {
        pokeService.send(userId, roomId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(Response.created("찔렀습니다.", null));
    }
}
