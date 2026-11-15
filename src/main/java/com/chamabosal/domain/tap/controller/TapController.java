package com.chamabosal.domain.tap.controller;

import com.chamabosal.domain.tap.dto.request.TapSendRequest;
import com.chamabosal.domain.tap.dto.request.MoktakCompleteRequest;
import com.chamabosal.domain.tap.dto.response.MoktakCompleteResponse;
import com.chamabosal.domain.tap.dto.response.ReceivedTapsResponse;
import com.chamabosal.domain.tap.dto.response.TapSendResponse;
import com.chamabosal.domain.tap.service.MoktakService;
import com.chamabosal.domain.tap.service.TapService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
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
@Tag(name = "[TAP] 이마 톡톡 API", description = "톡톡 보내기·받은 톡톡·재생 완료·목탁 수행 완료")
public class TapController {

    private final TapService tapService;
    private final MoktakService moktakService;

    @PostMapping("/rounds/{roundId}/taps")
    @Operation(summary = "이마 톡톡 보내기 (RESULT-03)",
            description = """
                    톡톡을 끝낼 때(다 침·건너뛰기·화면 나가기) 친 횟수를 한 번만 보냅니다. 0번(건너뛰기)도 보냅니다.
                    지목된 사람의 방어 시간 중에도 보낼 수 있습니다. 1번 이상이면 개인 공양미 5석(riceEarned)을 받습니다. 한 솥 적립(potEarned)은 아직 없습니다.
                    """)
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "저장 성공"),
            @ApiResponse(responseCode = "400", description = "COMMON_002 횟수가 0~999가 아님 (field: count)",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "ROOM_002 이 방 멤버가 아님 / TAP_002 지목된 본인",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "ROUND_001 없는 회차",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "409",
                    description = "TAP_001 톡톡이 없는 결과(PICKED 아님, 방어됨 포함) / TAP_003 이미 톡톡함 / TAP_004 톡톡 시간 지남",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<Response<TapSendResponse>> send(
            @Parameter(hidden = true) @AuthenticationPrincipal Long userId,
            @PathVariable Long roundId,
            @RequestBody @Valid TapSendRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(Response.created("톡톡했습니다.", tapService.send(userId, roundId, request.count())));
    }

    @GetMapping("/rounds/{roundId}/taps/received")
    @Operation(summary = "받은 톡톡 (RESULT-03)",
            description = "지목된 본인이 아직 재생하지 않은 받은 톡톡입니다. 건너뛴(0번) 톡톡은 빼고, 방어된 회차는 빈 목록입니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공"),
            @ApiResponse(responseCode = "403", description = "ROOM_002 이 방 멤버가 아님 / TAP_005 지목된 사람이 아님",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "ROUND_001 없는 회차",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<Response<ReceivedTapsResponse>> getReceived(
            @Parameter(hidden = true) @AuthenticationPrincipal Long userId,
            @PathVariable Long roundId
    ) {
        return ResponseEntity.ok(Response.ok(tapService.getReceived(userId, roundId)));
    }

    @PutMapping("/rounds/{roundId}/taps/received/played")
    @Operation(summary = "받은 톡톡 재생 완료 (RESULT-03)",
            description = "그때까지 받은 톡톡을 모두 재생됨으로 저장합니다. 이미 재생했으면 바꾸지 않고 성공합니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "저장 성공"),
            @ApiResponse(responseCode = "403", description = "ROOM_002 이 방 멤버가 아님 / TAP_005 지목된 사람이 아님",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "ROUND_001 없는 회차",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<Response<Void>> markPlayed(
            @Parameter(hidden = true) @AuthenticationPrincipal Long userId,
            @PathVariable Long roundId
    ) {
        tapService.markPlayed(userId, roundId);
        return ResponseEntity.ok(Response.ok(null));
    }

    @PostMapping("/rounds/{roundId}/moktak")
    @Operation(summary = "목탁 수행 완료 (RESULT-03, RICE-01)",
            description = """
                    지목된 사람이 받은 톡톡만큼 목탁을 끝까지 두드렸을 때 한 번 보냅니다. 번뇌 완료 보상 10석(riceEarned)을 받습니다.
                    중간에 포기하면 보내지 않습니다. 두드린 횟수(count)가 그 회차에 받은 톡톡 합계보다 적으면 MOKTAK_004로 거부합니다.
                    수행 중에 톡톡이 더 와서 거부되면 받은 톡톡을 다시 불러와 이어서 두드립니다.
                    이미 완료한 회차면 보상 없이 riceEarned 0으로 성공합니다. 받은 톡톡이 0번이거나 다음 투표가 열리면 보상이 없습니다(Q-92 임시 결정).
                    확인 순서: ROUND_001 → ROOM_002 → TAP_005 → MOKTAK_001 → 이미 완료(성공) → MOKTAK_002 → MOKTAK_003 → MOKTAK_004
                    """)
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "완료 성공"),
            @ApiResponse(responseCode = "400", description = "COMMON_002 두드린 횟수가 없거나 음수 (field: count)",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "ROOM_002 이 방 멤버가 아님 / TAP_005 지목된 사람이 아님",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "ROUND_001 없는 회차",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "409",
                    description = "MOKTAK_001 번뇌 보상이 없는 결과(PICKED 아님, 방어됨 포함) / MOKTAK_002 받은 톡톡 없음 / MOKTAK_003 다음 투표가 열려 기한 지남 / MOKTAK_004 받은 톡톡보다 적게 두드림",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<Response<MoktakCompleteResponse>> completeMoktak(
            @Parameter(hidden = true) @AuthenticationPrincipal Long userId,
            @PathVariable Long roundId,
            @Valid @RequestBody MoktakCompleteRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(Response.created("목탁 수행을 마쳤습니다.", moktakService.complete(userId, roundId, request.count())));
    }
}
