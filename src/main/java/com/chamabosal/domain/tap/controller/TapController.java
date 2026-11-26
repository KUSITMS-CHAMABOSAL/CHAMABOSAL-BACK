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
@Tag(name = "[TAP] 이마 톡톡 API", description = "톡톡 시작·보내기·받은 톡톡·목탁 수행")
public class TapController {

    private final TapService tapService;
    private final MoktakService moktakService;

    @PostMapping("/rounds/{roundId}/taps/start")
    @Operation(summary = "이마 톡톡 시작 (RESULT-03)",
            description = """
                    톡톡 화면에 들어갈 때 한 번 보냅니다. 방어 전에 시작했다는 기록이라, 지목된 사람이 그사이 방어해도 끝까지 보낼 수 있습니다.
                    보내기와 같은 조건을 확인하고, 다시 보내도 성공합니다. 이미 방어된 결과면 TAP_001입니다.
                    """)
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "시작을 처음 남김"),
            @ApiResponse(responseCode = "200", description = "이미 시작함 (재요청)"),
            @ApiResponse(responseCode = "403", description = "ROOM_002 이 방 멤버가 아님 / TAP_002 지목된 본인 / TAP_006 그 회차 멤버가 아님(투표 시작 뒤 들어옴)",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "ROUND_001 없는 회차",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "409",
                    description = "TAP_001 톡톡이 없는 결과(PICKED 아님, 방어됨, 지목된 사람이 나감 포함) / TAP_003 이미 톡톡함 / TAP_004 톡톡 시간 지남",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<Response<Void>> start(
            @Parameter(hidden = true) @AuthenticationPrincipal Long userId,
            @PathVariable Long roundId
    ) {
        if (tapService.start(userId, roundId)) {
            return ResponseEntity.status(HttpStatus.CREATED).body(Response.created("톡톡을 시작했어요.", null));
        }
        return ResponseEntity.ok(Response.ok("이미 톡톡을 시작했어요.", null));
    }

    @PostMapping("/rounds/{roundId}/taps")
    @Operation(summary = "이마 톡톡 보내기 (RESULT-03)",
            description = """
                    톡톡을 끝낼 때(다 침·건너뛰기·화면 나가기) 친 횟수를 한 번만 보냅니다. 0번(건너뛰기)도 보냅니다. 횟수 상한은 없습니다.
                    투표·인증과 상관없이 지목된 사람을 뺀 그 회차 멤버면 보낼 수 있습니다. 투표 시작 뒤 들어온 멤버는 TAP_006입니다.
                    지목된 사람의 방어 시간 중에도 보낼 수 있습니다. 방어된 뒤에는 방어 전에 taps/start를 보낸 사람만 보낼 수 있고, 톡톡은 전달되지 않지만 공양미는 받습니다. 1번 이상이면 개인 공양미 5석(riceEarned)을 받고 한 솥에도 5석(potEarned)이 들어갑니다.
                    """)
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "저장 성공"),
            @ApiResponse(responseCode = "400", description = "COMMON_002 횟수가 0보다 작음 (field: count)",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "ROOM_002 이 방 멤버가 아님 / TAP_002 지목된 본인 / TAP_006 그 회차 멤버가 아님(투표 시작 뒤 들어옴)",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "ROUND_001 없는 회차",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "409",
                    description = "TAP_001 톡톡이 없는 결과(PICKED 아님, 방어됨, 지목된 사람이 나감 포함) / TAP_003 이미 톡톡함 / TAP_004 톡톡 시간 지남",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<Response<TapSendResponse>> send(
            @Parameter(hidden = true) @AuthenticationPrincipal Long userId,
            @PathVariable Long roundId,
            @RequestBody @Valid TapSendRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(Response.created("톡톡했어요.", tapService.send(userId, roundId, request.count())));
    }

    @GetMapping("/rounds/{roundId}/taps/received")
    @Operation(summary = "받은 톡톡 (RESULT-03)",
            description = "지목된 본인이 받은 톡톡입니다. 건너뛴(0번) 톡톡은 빼고, 방어된 회차는 빈 목록입니다. 투표 마감 후 5시간에 전달되고, 지목된 사람을 뺀 회차 멤버 전원이 보냈으면 지목된 사람의 방어 시간(결과를 처음 연 뒤 2분) 뒤 바로 전달됩니다. 전달 전에는 빈 목록과 전달 시각(deliveredAt)을 줍니다.")
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

    @PostMapping("/rounds/{roundId}/moktak")
    @Operation(summary = "목탁 수행 완료 (RESULT-03, RICE-01)",
            description = """
                    지목된 사람이 받은 톡톡만큼 목탁을 끝까지 두드렸을 때 한 번 보냅니다. 번뇌 완료 보상 2석(riceEarned)을 받습니다.
                    [종료하기]로 중간에 나갈 때도 두드린 횟수와 함께 보냅니다. 두드린 횟수(count)가 받은 톡톡 합계보다 적으면 보상 없이(riceEarned 0) 끝납니다. 어느 쪽이든 그 회차 목탁 수행은 끝나서 투표 탭에 MOKTAK이 더 나오지 않습니다.
                    수행 중에 톡톡이 더 와서 거부되면 받은 톡톡을 다시 불러와 이어서 두드립니다.
                    이미 완료한 회차면 보상 없이 riceEarned 0으로 성공합니다. 받은 톡톡이 0번이거나 다음 투표가 열리면 보상이 없습니다(Q-92). 톡톡이 전달되기 전이면 MOKTAK_005입니다. 전달은 보통 투표 마감 후 5시간이고, 지목된 사람을 뺀 회차 멤버 전원이 보냈으면 지목된 사람의 방어 시간 뒤 바로입니다.
                    확인 순서: ROUND_001 → ROOM_002 → TAP_005 → MOKTAK_001 → 이미 끝냄(성공) → MOKTAK_005 → MOKTAK_002 → MOKTAK_003
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
                    description = "MOKTAK_001 번뇌 보상이 없는 결과(PICKED 아님, 방어됨 포함) / MOKTAK_002 받은 톡톡 없음 / MOKTAK_003 다음 투표가 열려 기한 지남 / MOKTAK_005 톡톡이 아직 전달되지 않음",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<Response<MoktakCompleteResponse>> completeMoktak(
            @Parameter(hidden = true) @AuthenticationPrincipal Long userId,
            @PathVariable Long roundId,
            @Valid @RequestBody MoktakCompleteRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(Response.created("목탁 수행을 마쳤어요.", moktakService.complete(userId, roundId, request.count())));
    }
}
