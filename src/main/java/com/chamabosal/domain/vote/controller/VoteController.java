package com.chamabosal.domain.vote.controller;

import com.chamabosal.domain.vote.dto.request.VoteSubmitRequest;
import com.chamabosal.domain.vote.dto.response.VoteSubmitResponse;
import com.chamabosal.domain.vote.dto.response.VoteTabStatusResponse;
import com.chamabosal.domain.vote.service.VoteSubmitService;
import com.chamabosal.domain.vote.service.VoteTabStatusService;
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
@Tag(name = "[VOTE] 투표 API", description = "후보 조회·투표 제출·투표 탭 상태")
public class VoteController {

    private final VoteSubmitService voteSubmitService;
    private final VoteTabStatusService voteTabStatusService;

    @PostMapping("/rounds/{roundId}/votes")
    @Operation(summary = "투표 제출 (VOTE-03)",
            description = "후보 또는 \"오늘은 모두 보살\"에 투표합니다. 방 인원이 2명 이상이면 개인 공양미 5석을 받습니다(riceEarned). 한 솥 적립(potEarned)은 아직 없습니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "제출 성공"),
            @ApiResponse(responseCode = "400", description = "COMMON_002 입력값 오류",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403",
                    description = "ROOM_002 이 방 멤버가 아님 / VOTE_002 명단 밖 / VOTE_003 투표권 없음",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "ROUND_001 없는 회차",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "409",
                    description = "VOTE_001 투표 중 아님 / VOTE_004 이미 투표함",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<Response<VoteSubmitResponse>> submit(
            @Parameter(hidden = true) @AuthenticationPrincipal Long userId,
            @PathVariable Long roundId,
            @RequestBody @Valid VoteSubmitRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(Response.created("투표했습니다.", voteSubmitService.submit(userId, roundId, request)));
    }

    @GetMapping("/rooms/{roomId}/vote")
    @Operation(summary = "투표 탭 상태 (VOTE-05)", description = "사용자와 방의 상황에 맞는 투표 탭 상태를 반환합니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공"),
            @ApiResponse(responseCode = "403", description = "ROOM_002 이 방 멤버가 아님",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "ROOM_005 없거나 삭제된 방",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<Response<VoteTabStatusResponse>> getStatus(
            @Parameter(hidden = true) @AuthenticationPrincipal Long userId,
            @PathVariable Long roomId
    ) {
        return ResponseEntity.ok(Response.ok(voteTabStatusService.getStatus(userId, roomId)));
    }
}
