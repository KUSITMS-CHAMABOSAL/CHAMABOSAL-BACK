package com.chamabosal.domain.certification.controller;

import com.chamabosal.domain.certification.dto.request.CertificationCreateRequest;
import com.chamabosal.domain.certification.dto.request.CertificationUpdateRequest;
import com.chamabosal.domain.certification.dto.response.CertificationCreateResponse;
import com.chamabosal.domain.certification.dto.response.CertificationDeleteResponse;
import com.chamabosal.domain.certification.dto.response.CertificationUpdateResponse;
import com.chamabosal.domain.certification.service.CertificationService;
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
@RequestMapping("/api")
@Tag(name = "[CERT] 인증 API", description = "인증 등록·수정·삭제")
public class CertificationController {

    private final CertificationService certificationService;

    @PostMapping("/rooms/{roomId}/certifications")
    @Operation(summary = "인증 등록 (CERT-01)",
            description = """
                    오늘의 소비 절약 결과를 인증합니다. 회차는 서버가 요청을 받은 시각으로 정합니다.
                    03:00(KST) 전이면 전날 회차, 03:00부터 그날 회차입니다. 그날 회차 인증은 이전 회차 투표가 마감돼야 열립니다.
                    한 줄 기록은 1~40자(이모지도 한 글자)이고 띄어쓰기만 있으면 안 됩니다. FAILED면 금액 1~999,999원이 필수입니다.
                    방 인원이 2명 이상이면 개인 공양미 10석을 받습니다(riceEarned). 아직 사진(imageKey)과 한 솥 적립(potEarned)은 없습니다.
                    """)
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "등록 성공"),
            @ApiResponse(
                    responseCode = "400",
                    description = "COMMON_002 입력값 오류 (field: result, memo, spentAmount)",
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
                    description = "CERT_001 이미 인증함 / CERT_006 아직 인증 시간이 아님 (이전 회차 투표가 끝나지 않았거나, 이번 회차 투표가 이미 시작됨)",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class)
                    )
            )
    })
    public ResponseEntity<Response<CertificationCreateResponse>> create(
            @Parameter(hidden = true) @AuthenticationPrincipal Long userId,
            @PathVariable Long roomId,
            @RequestBody @Valid CertificationCreateRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(Response.created("인증했습니다.", certificationService.create(userId, roomId, request)));
    }

    @PatchMapping("/certifications/{certificationId}")
    @Operation(summary = "인증 수정 (CERT-02)",
            description = """
                    등록 후 10분 안에, 투표가 시작되기 전까지 수정합니다. 등록처럼 모든 값을 보내 통째로 바꾸고 공양미를 다시 주지 않습니다.
                    ENDURED로 바꾸면 저장된 금액을 지웁니다. 투표가 열리지 않은 회차는 10분까지 수정할 수 있습니다.
                    확인 순서: 입력값(COMMON_002) → 없거나 삭제된 인증(CERT_002) → 본인 인증 아님(CERT_003) → 수정 시간 지남(CERT_004) → 투표 시작(CERT_005).
                    아직 사진(imageKey, isImageKept)은 없습니다.
                    """)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "수정 성공"),
            @ApiResponse(responseCode = "400", description = "COMMON_002 입력값 오류 (field: result, memo, spentAmount)",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "CERT_003 본인 인증이 아님",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "CERT_002 없거나 삭제된 인증",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "CERT_004 수정 시간 지남 / CERT_005 투표 시작",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<Response<CertificationUpdateResponse>> update(
            @Parameter(hidden = true) @AuthenticationPrincipal Long userId,
            @PathVariable Long certificationId,
            @RequestBody @Valid CertificationUpdateRequest request
    ) {
        return ResponseEntity.ok(Response.ok("인증을 수정했습니다.",
                certificationService.update(userId, certificationId, request)));
    }

    @DeleteMapping("/certifications/{certificationId}")
    @Operation(summary = "인증 삭제 (CERT-02)",
            description = """
                    등록 후 10분 안에, 투표가 시작되기 전까지 삭제합니다. 그 인증으로 받은 개인 공양미를 회수하고, 같은 회차에 다시 인증할 수 있습니다.
                    이미 삭제한 내 인증을 다시 삭제하면 회수 없이(riceRecalled: 0) 성공합니다. 이 확인은 잠금 검사보다 먼저 합니다.
                    확인 순서: 없는 인증(CERT_002) → 본인 인증 아님(CERT_003) → 이미 삭제됨(성공) → 수정 시간 지남(CERT_004) → 투표 시작(CERT_005).
                    아직 한 솥 회수(potRecalled)는 없습니다.
                    """)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "삭제 성공"),
            @ApiResponse(responseCode = "403", description = "CERT_003 본인 인증이 아님",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "CERT_002 없는 인증",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "CERT_004 수정 시간 지남 / CERT_005 투표 시작",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<Response<CertificationDeleteResponse>> delete(
            @Parameter(hidden = true) @AuthenticationPrincipal Long userId,
            @PathVariable Long certificationId
    ) {
        return ResponseEntity.ok(Response.ok("인증을 삭제했습니다.", certificationService.delete(userId, certificationId)));
    }
}
