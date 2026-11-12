package com.chamabosal.domain.room.controller;

import com.chamabosal.domain.room.dto.request.InviteJoinRequest;
import com.chamabosal.domain.room.dto.response.InviteJoinResponse;
import com.chamabosal.domain.room.dto.response.InvitePreviewResponse;
import com.chamabosal.domain.room.dto.response.InviteTokenResponse;
import com.chamabosal.domain.room.service.CreationResult;
import com.chamabosal.domain.room.service.InviteService;
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
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api")
@Tag(name = "[INVITE] 초대 API", description = "초대 토큰, 초대 미리보기, 초대로 입장")
public class InviteController {

    private final InviteService inviteService;

    @PostMapping("/rooms/{roomId}/members/me/invite-token")
    @Operation(summary = "초대 토큰 (INVITE-01)",
            description = "내 초대 토큰을 돌려줍니다. 없으면 만들고(201), 있으면 그대로 돌려줍니다(200). 클라이언트는 이 토큰으로 공유 링크를 만듭니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "이미 있는 토큰"),
            @ApiResponse(responseCode = "201", description = "새로 만든 토큰"),
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
                    description = "ROOM_005 없거나 삭제된 방",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class)
                    )
            )
    })
    public ResponseEntity<Response<InviteTokenResponse>> issueToken(
            @Parameter(hidden = true) @AuthenticationPrincipal Long userId,
            @PathVariable Long roomId
    ) {
        CreationResult<InviteTokenResponse> result = inviteService.issueToken(userId, roomId);
        if (result.isCreated()) {
            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(Response.created("초대 토큰을 만들었습니다.", result.response()));
        }
        return ResponseEntity.ok(Response.ok(result.response()));
    }

    @GetMapping("/invites/{token}")
    @Operation(summary = "초대 미리보기 (INVITE-02)",
            description = """
                    초대 링크를 연 사용자가 들어갈 수 있는지 판단합니다. 막힌 경우도 200으로 보내고 status로 화면을 나눕니다.
                    판단 순서: 없는 토큰·방 삭제·초대한 사람이 내보내짐(UNAVAILABLE) → 이미 멤버(ALREADY_MEMBER)
                    → 내가 내보내짐(UNAVAILABLE) → 참여 방 2개(ROOM_LIMIT) → 방이 가득 참(ROOM_FULL) → JOINABLE.
                    UNAVAILABLE이면 status 말고는 모두 null입니다.
                    """)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "판단 성공"),
            @ApiResponse(
                    responseCode = "401",
                    description = "COMMON_008 로그인 필요",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class)
                    )
            )
    })
    public ResponseEntity<Response<InvitePreviewResponse>> preview(
            @Parameter(hidden = true) @AuthenticationPrincipal Long userId,
            @PathVariable String token
    ) {
        return ResponseEntity.ok(Response.ok(inviteService.preview(userId, token)));
    }

    @PostMapping("/invites/{token}/join")
    @Operation(summary = "초대로 입장 (INVITE-03)",
            description = """
                    초대 링크로 방에 들어옵니다(201). 이미 멤버면 새로 만들지 않고 기존 멤버 정보를 돌려줍니다(200).
                    나간 지 6개월이 안 됐으면 예전 멤버 행을 다시 써서 기록이 이어지고, 예전 닉네임을 그대로 쓸 수 있습니다.
                    예전 색을 다른 멤버가 쓰고 있으면 남은 색 중 랜덤으로 정합니다.
                    """)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "이미 멤버"),
            @ApiResponse(responseCode = "201", description = "입장 성공"),
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
            ),
            @ApiResponse(
                    responseCode = "403",
                    description = "ROOM_006 들어갈 수 없는 방",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "409",
                    description = "ROOM_001 참여 중인 방이 2개 / ROOM_003 방이 가득 참 / ROOM_004 이미 쓰는 닉네임 (field: nickname)",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class)
                    )
            )
    })
    public ResponseEntity<Response<InviteJoinResponse>> join(
            @Parameter(hidden = true) @AuthenticationPrincipal Long userId,
            @PathVariable String token,
            @RequestBody @Valid InviteJoinRequest request
    ) {
        CreationResult<InviteJoinResponse> result = inviteService.join(userId, token, request);
        if (result.isCreated()) {
            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(Response.created("방에 들어왔습니다.", result.response()));
        }
        return ResponseEntity.ok(Response.ok(result.response()));
    }
}
