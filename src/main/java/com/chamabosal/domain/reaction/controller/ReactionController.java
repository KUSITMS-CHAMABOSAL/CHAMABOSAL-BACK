package com.chamabosal.domain.reaction.controller;

import com.chamabosal.domain.reaction.dto.request.ReactionPutRequest;
import com.chamabosal.domain.reaction.dto.response.ReactionPutResponse;
import com.chamabosal.domain.reaction.service.ReactionService;
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
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/certifications")
@Tag(name = "[REACT] 리액션 API", description = "인증 리액션 남기기·바꾸기·취소")
public class ReactionController {

    private final ReactionService reactionService;

    @PutMapping("/{certificationId}/reactions/me")
    @Operation(summary = "리액션 남기기·바꾸기 (REACT-01)",
            description = """
                    다른 멤버의 인증에 GOOD(잘 참았어요) 또는 BAD(아쉬워요)를 남기거나 바꿉니다. 같은 값으로 다시 보내도 그대로입니다.
                    같은 리액션을 다시 눌러 취소할 때는 DELETE를 부릅니다. 인증이 속한 회차가 인증 중일 때만 됩니다.
                    이 인증에 처음 남길 때만 개인 공양미 1석(riceEarned)을 받고 한 솥에도 1석(potEarned)이 들어갑니다. 방 인원이 1명이면 0입니다.
                    확인 순서: COMMON_002 → CERT_002 → ROOM_002 → REACT_001 → REACT_002
                    """)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "저장 성공"),
            @ApiResponse(
                    responseCode = "400",
                    description = "COMMON_002 없는 리액션 종류 (field: type)",
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
                    description = "ROOM_002 인증이 있는 방의 멤버가 아님 / REACT_001 내 인증",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "CERT_002 없거나 삭제된 인증",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "409",
                    description = "REACT_002 투표가 시작됨",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class)
                    )
            )
    })
    public ResponseEntity<Response<ReactionPutResponse>> put(
            @Parameter(hidden = true) @AuthenticationPrincipal Long userId,
            @PathVariable Long certificationId,
            @RequestBody @Valid ReactionPutRequest request
    ) {
        return ResponseEntity.ok(Response.ok(reactionService.put(userId, certificationId, request.type())));
    }

    @DeleteMapping("/{certificationId}/reactions/me")
    @Operation(summary = "리액션 취소 (REACT-01)",
            description = """
                    남긴 리액션을 취소합니다. 남긴 리액션이 없으면 투표가 시작된 뒤라도 성공합니다. 받은 공양미는 회수하지 않습니다.
                    확인 순서: CERT_002 → ROOM_002 → 남긴 리액션 없음(성공) → REACT_001 → REACT_002
                    """)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "취소 성공"),
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
                    description = "ROOM_002 인증이 있는 방의 멤버가 아님 / REACT_001 내 인증",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "CERT_002 없거나 삭제된 인증",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "409",
                    description = "REACT_002 투표가 시작됨",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class)
                    )
            )
    })
    public ResponseEntity<Response<Void>> cancel(
            @Parameter(hidden = true) @AuthenticationPrincipal Long userId,
            @PathVariable Long certificationId
    ) {
        reactionService.cancel(userId, certificationId);
        return ResponseEntity.ok(Response.ok(null));
    }
}
