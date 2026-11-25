package com.chamabosal.domain.room.controller;

import com.chamabosal.domain.room.dto.request.RoomCreateRequest;
import com.chamabosal.domain.room.dto.request.RoomNicknameUpdateRequest;
import com.chamabosal.domain.room.dto.request.RoomSeenResultUpdateRequest;
import com.chamabosal.domain.room.dto.request.RoomSeenPotUpdateRequest;
import com.chamabosal.domain.room.dto.response.LeavePreviewResponse;
import com.chamabosal.domain.room.dto.response.RoomCreateResponse;
import com.chamabosal.domain.room.dto.response.RoomNewColorResponse;
import com.chamabosal.domain.room.dto.response.RoomTodayResponse;
import com.chamabosal.domain.room.service.RoomService;
import com.chamabosal.domain.room.service.RoomTodayService;
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
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/rooms")
@Tag(name = "[ROOM] 방 API", description = "색 뽑기, 방 만들기, 오늘 탭, 닉네임 수정, 결과 확인, 한 솥 확인, 나가기 미리보기, 나가기, 내보내기")
public class RoomController {

    private final RoomService roomService;
    private final RoomTodayService roomTodayService;

    @GetMapping("/{roomId}/today")
    @Operation(summary = "오늘 탭 (ROOM-02)",
            description = """
                    지금 회차 날짜(03:00 전이면 전날)의 회차를 기준으로 방·한 솥·회차·멤버·진입 항목을 돌려줍니다.
                    round.isCertificationOpen이 false면 이전 회차 투표가 아직 끝나지 않아 인증할 수 없습니다(03:00~15:00 어제 투표 중 등).
                    entry는 앱을 열거나 방에 들어올 때 홈보다 먼저 띄울 화면입니다. 미인증 투표 팝업·탈모 → 결과 → 한 솥 축하 → 방장 선택 팝업 → 방 홈 순서입니다.
                    이마 톡톡·목탁은 앱을 열 때 띄우지 않고 투표 탭에서만 진행합니다(Q-107).
                    """)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공"),
            @ApiResponse(responseCode = "401", description = "COMMON_008 로그인 필요",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "ROOM_002 이 방 멤버가 아님",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "ROOM_005 없거나 삭제된 방",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<Response<RoomTodayResponse>> getToday(
            @Parameter(hidden = true) @AuthenticationPrincipal Long userId,
            @PathVariable Long roomId
    ) {
        return ResponseEntity.ok(Response.ok(roomTodayService.getToday(userId, roomId)));
    }

    @GetMapping("/new-color")
    @Operation(summary = "보살 색 뽑기 (ROOM-01)",
            description = "방 만들기 전 내 보살 받기 화면에서 씁니다. 4종 중 하나를 랜덤으로 돌려주고 저장하지 않아 부를 때마다 새로 뽑습니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "뽑기 성공"),
            @ApiResponse(responseCode = "401", description = "COMMON_008 로그인 필요",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<Response<RoomNewColorResponse>> drawNewColor() {
        return ResponseEntity.ok(Response.ok(roomService.drawNewColor()));
    }

    @PostMapping
    @Operation(summary = "방 만들기 (ROOM-01)",
            description = "방과 방장 멤버를 한 번에 만듭니다. 보살 색은 색 뽑기(GET /api/rooms/new-color)에서 받은 색으로 확정합니다. 방 이름 앞뒤 띄어쓰기는 지우고 저장합니다. 공양미 목표는 300~700석 중 50석 단위입니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "생성 성공"),
            @ApiResponse(
                    responseCode = "400",
                    description = "COMMON_002 입력값 오류 (field: name, memberLimit, potGoalAmount, nickname, color 등)",
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
                .body(Response.created("방을 만들었어요.", roomService.create(userId, request)));
    }

    @PatchMapping("/{roomId}/members/me/nickname")
    @Operation(summary = "닉네임 수정 (ROOM-04)",
            description = """
                    이 방에서 쓰는 내 닉네임을 바꿉니다.
                    앞뒤 띄어쓰기는 지우고, 다른 멤버와 같은 닉네임도 쓸 수 있습니다.
                    지금 닉네임과 같으면 저장하지 않고 성공합니다.
                    투표 중인 회차가 있으면(어제 회차 포함) 바꿀 수 없습니다.
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
                    description = "ROOM_012 투표 중 닉네임 수정",
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

    @PutMapping("/{roomId}/members/me/seen-result")
    @Operation(summary = "결과 확인 (ROOM-06)",
            description = """
                    결과를 어디까지 봤는지 저장합니다. 결과 화면을 본 뒤 그 회차 ID를 보내 주세요. 그 회차와 이전 회차를 모두 본 것으로 처리합니다.
                    이미 저장한 회차보다 이전 회차를 보내면 바꾸지 않고 성공합니다. 이 방의 발표된 회차가 아니면 COMMON_002입니다.
                    """)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "저장 성공"),
            @ApiResponse(responseCode = "400", description = "COMMON_002 이 방의 발표된 회차가 아님 (field: roundId)",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "ROOM_002 이 방 멤버가 아님",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "ROOM_005 없거나 삭제된 방",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<Response<Void>> markResultSeen(
            @Parameter(hidden = true) @AuthenticationPrincipal Long userId,
            @PathVariable Long roomId,
            @RequestBody @Valid RoomSeenResultUpdateRequest request
    ) {
        roomService.markResultSeen(userId, roomId, request);
        return ResponseEntity.ok(Response.ok(null));
    }

    @PutMapping("/{roomId}/members/me/seen-pot")
    @Operation(summary = "한 솥 축하 확인 (ROOM-06)",
            description = """
                    한 솥 축하를 몇 번째 솥까지 봤는지 저장합니다. 축하를 본 뒤 오늘 탭의 room.potCompletedCount를 그대로 보내 주세요.
                    저장값보다 작거나 같으면 바꾸지 않고 성공합니다. 방의 완성 횟수보다 크거나 음수면 COMMON_002입니다.
                    """)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "저장 성공"),
            @ApiResponse(
                    responseCode = "400",
                    description = "COMMON_002 입력값 오류 (field: seenPotCount)",
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
            )
    })
    public ResponseEntity<Response<Void>> markPotSeen(
            @Parameter(hidden = true) @AuthenticationPrincipal Long userId,
            @PathVariable Long roomId,
            @RequestBody @Valid RoomSeenPotUpdateRequest request
    ) {
        roomService.markPotSeen(userId, roomId, request);
        return ResponseEntity.ok(Response.ok(null));
    }

    @GetMapping("/{roomId}/members/me/leave-preview")
    @Operation(summary = "나가기 공양미 미리보기 (RICE-03)",
            description = """
                    나가기 확인 화면에 보여줄, 지금 나가면 사라질 공양미 석입니다. 실제 차감과 같은 계산입니다.
                    목표를 채운 방이거나 잔액이 0석 이하면 0입니다. 화면을 본 뒤 공양미가 바뀌면 실제 차감은 나갈 때의 값입니다.
                    확인 순서: ROOM_005 → ROOM_002
                    """)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공"),
            @ApiResponse(responseCode = "401", description = "COMMON_008 로그인 필요",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "ROOM_002 이 방 멤버가 아님",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "ROOM_005 없거나 삭제된 방",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<Response<LeavePreviewResponse>> getLeavePreview(
            @Parameter(hidden = true) @AuthenticationPrincipal Long userId,
            @PathVariable Long roomId
    ) {
        return ResponseEntity.ok(Response.ok(roomService.getLeavePreview(userId, roomId)));
    }

    @DeleteMapping("/{roomId}/members/me")
    @Operation(summary = "나가기 (ROOM-07)",
            description = """
                    방을 나갑니다. 방장이면 남은 멤버 중 가장 먼저 들어온 멤버에게 위임하고, 마지막 멤버면 방을 삭제합니다.
                    방이 남아 있으면 나간 뒤 6개월 안에 초대 링크로 다시 들어올 때 기록이 이어집니다.
                    목표를 채우기 전이면 이 방에서 모은 공양미를 차감합니다(미리보기는 leave-preview).
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

    @PostMapping("/{roomId}/pot-decision/continue")
    @Operation(summary = "목표 달성 뒤 더 수행하기 (ROOM-09)",
            description = """
                    솥을 채운 뒤 방장이 [더 수행하기]를 고릅니다. 선택 대기가 끝나고 솥·회차·멤버는 그대로입니다.
                    다음 솥은 솥을 채우는 순간 이미 시작돼 있습니다(POT-01). 선택 대기는 오늘 탭 me.isPotDecisionPending입니다.
                    확인 순서: ROOM_005 → ROOM_002 → ROOM_007 → ROOM_011
                    """)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "선택 대기 끝"),
            @ApiResponse(responseCode = "401", description = "COMMON_008 로그인 필요",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "ROOM_002 이 방 멤버가 아님 / ROOM_007 방장이 아님",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "ROOM_005 없거나 삭제된 방",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "ROOM_011 선택 대기가 아님",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<Response<Void>> continuePot(
            @Parameter(hidden = true) @AuthenticationPrincipal Long userId,
            @PathVariable Long roomId
    ) {
        roomService.continuePot(userId, roomId);
        return ResponseEntity.ok(Response.ok(null));
    }

    @DeleteMapping("/{roomId}")
    @Operation(summary = "목표 달성 뒤 방 닫기 (ROOM-09)",
            description = """
                    솥을 채운 뒤 방장이 [방 닫기]를 고릅니다. 멤버 전원이 나간 것으로 처리하고 방을 삭제합니다.
                    목표를 채운 방이라 공양미 차감은 없고, 이 방의 인증 사진은 바로 지웁니다. 선택 대기일 때만 닫을 수 있습니다.
                    확인 순서: ROOM_005 → ROOM_002 → ROOM_007 → ROOM_011
                    """)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "방 삭제"),
            @ApiResponse(responseCode = "401", description = "COMMON_008 로그인 필요",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "ROOM_002 이 방 멤버가 아님 / ROOM_007 방장이 아님",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "ROOM_005 없거나 삭제된 방",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "ROOM_011 선택 대기가 아님",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<Response<Void>> close(
            @Parameter(hidden = true) @AuthenticationPrincipal Long userId,
            @PathVariable Long roomId
    ) {
        roomService.close(userId, roomId);
        return ResponseEntity.ok(Response.ok(null));
    }

    @DeleteMapping("/{roomId}/members/{memberId}")
    @Operation(summary = "내보내기 (ROOM-08)",
            description = """
                    방장이 멤버를 내보냅니다. 내보낸 사람은 같은 방에 다시 들어올 수 없고, 내보낸 사유는 어디에도 표시하지 않습니다.
                    방에 투표 중인 회차가 있으면 막습니다(어제 회차 포함, 오늘 탭 room.hasVotingRound). 인증 중에 내보내서 남은 멤버 전원이 인증한 상태가 되면 바로 투표를 시작합니다.
                    내보내진 사람이 목표를 채우기 전의 이 방에서 모은 공양미는 나가기처럼 차감합니다(Q-94).
                    확인 순서: ROOM_005 → ROOM_002 → ROOM_007 → ROOM_010 → ROOM_009 → ROOM_008
                    """)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "내보내기 성공"),
            @ApiResponse(responseCode = "400", description = "ROOM_010 방장 본인",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "COMMON_008 로그인 필요",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "ROOM_002 이 방 멤버가 아님 / ROOM_007 방장이 아님",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "ROOM_005 없거나 삭제된 방 / ROOM_009 대상이 이 방의 현재 멤버가 아님",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "ROOM_008 투표 중인 회차가 있음",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<Response<Void>> kick(
            @Parameter(hidden = true) @AuthenticationPrincipal Long userId,
            @PathVariable Long roomId,
            @PathVariable Long memberId
    ) {
        roomService.kick(userId, roomId, memberId);
        return ResponseEntity.ok(Response.ok(null));
    }
}
