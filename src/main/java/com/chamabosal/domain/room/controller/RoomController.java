package com.chamabosal.domain.room.controller;

import com.chamabosal.domain.room.dto.request.RoomCreateRequest;
import com.chamabosal.domain.room.dto.request.RoomNicknameUpdateRequest;
import com.chamabosal.domain.room.dto.response.RoomCreateResponse;
import com.chamabosal.domain.room.service.RoomService;
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
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/rooms")
@Tag(name = "[ROOM] 방 API", description = "방 만들기, 닉네임 수정, 나가기")
public class RoomController {

    private final RoomService roomService;

    @PostMapping
    @Operation(summary = "방 만들기 (ROOM-01)",
            description = "방과 방장 멤버를 한 번에 만듭니다. 보살 색은 서버가 랜덤으로 정합니다. 방 이름 앞뒤 띄어쓰기는 지우고 저장합니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "생성 성공"),
            @ApiResponse(
                    responseCode = "400",
                    description = "COMMON_002 입력값 오류 (field: name, memberLimit, nickname 등)",
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
                    responseCode = "409",
                    description = "ROOM_001 참여 중인 방이 2개",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class)
                    )
            )
    })
    public ResponseEntity<Response<RoomCreateResponse>> create(
            @Parameter(hidden = true) @AuthenticationPrincipal Long userId,
            @RequestBody @Valid RoomCreateRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(Response.created("방을 만들었습니다.", roomService.create(userId, request)));
    }

    @PatchMapping("/{roomId}/members/me/nickname")
    @Operation(summary = "닉네임 수정 (ROOM-04)",
            description = """
                    이 방에서 쓰는 내 닉네임을 바꿉니다.
                    현재 멤버와 나간 지 6개월이 안 된 멤버의 닉네임은 쓸 수 없고, 영문 대소문자는 구분하지 않습니다.
                    지금 닉네임과 같으면 저장하지 않고 성공합니다.
                    """)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "수정 성공"),
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
            ),
            @ApiResponse(
                    responseCode = "409",
                    description = "ROOM_004 이미 쓰는 닉네임 (field: nickname)",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class)
                    )
            )
    })
    public ResponseEntity<Response<Void>> updateNickname(
            @Parameter(hidden = true) @AuthenticationPrincipal Long userId,
            @PathVariable Long roomId,
            @RequestBody @Valid RoomNicknameUpdateRequest request
    ) {
        roomService.updateNickname(userId, roomId, request);
        return ResponseEntity.ok(Response.ok(null));
    }

    @DeleteMapping("/{roomId}/members/me")
    @Operation(summary = "나가기 (ROOM-07)",
            description = """
                    방을 나갑니다. 방장이면 남은 멤버 중 가장 먼저 들어온 멤버에게 위임하고, 마지막 멤버면 방을 삭제합니다.
                    방이 남아 있으면 나간 뒤 6개월 안에 초대 링크로 다시 들어올 때 기록이 이어집니다.
                    """)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "나가기 성공"),
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
    public ResponseEntity<Response<Void>> leave(
            @Parameter(hidden = true) @AuthenticationPrincipal Long userId,
            @PathVariable Long roomId
    ) {
        roomService.leave(userId, roomId);
        return ResponseEntity.ok(Response.ok(null));
    }
}
