package com.chamabosal.domain.certification.controller;

import java.time.Clock;
import java.time.LocalDateTime;
import com.chamabosal.domain.certification.dto.request.CertificationCreateRequest;
import com.chamabosal.domain.certification.dto.request.CertificationUpdateRequest;
import com.chamabosal.domain.certification.dto.request.PhotoUploadUrlRequest;
import com.chamabosal.domain.certification.dto.response.CertificationCreateResponse;
import com.chamabosal.domain.certification.dto.response.CertificationDeleteResponse;
import com.chamabosal.domain.certification.dto.response.CertificationUpdateResponse;
import com.chamabosal.domain.certification.dto.response.PhotoUploadUrlResponse;
import com.chamabosal.domain.certification.photo.CertificationPhotoService;
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
    private final CertificationPhotoService certificationPhotoService;
    private final Clock clock;

    @PostMapping("/certifications/upload-urls")
    @Operation(summary = "인증 사진 업로드 URL 발급 (CERT-04)",
            description = """
                    사진을 올릴 presigned URL(5분 유효)과 imageKey를 발급합니다. uploadUrl에 PUT으로 사진을 올리고,
                    Content-Type 헤더는 요청한 contentType과 같아야 합니다. 올린 뒤 인증 등록·수정 요청에 imageKey를 넣습니다.
                    jpeg, png, heic, webp만 받고, 10MB 상한은 화면에서 지켜 주세요(Q-30 임시 결정). imageKey는 올린 뒤 1일 동안 쓸 수 있습니다.
                    """)
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "발급 성공"),
            @ApiResponse(responseCode = "400", description = "COMMON_002 허용하지 않는 사진 형식 (field: contentType)",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "COMMON_008 로그인 필요",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<Response<PhotoUploadUrlResponse>> issueUploadUrl(
            @Parameter(hidden = true) @AuthenticationPrincipal Long userId,
            @RequestBody @Valid PhotoUploadUrlRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(Response.created("업로드 URL을 발급했습니다.",
                        certificationPhotoService.issueUploadUrl(userId, request.contentType())));
    }

    @PostMapping("/rooms/{roomId}/certifications")
    @Operation(summary = "인증 등록 (CERT-01)",
            description = """
                    오늘의 소비 절약 결과를 인증합니다. 회차는 서버가 요청을 받은 시각으로 정합니다.
                    03:00(KST) 전이면 전날 회차, 03:00부터 그날 회차입니다. 그날 회차 인증은 이전 회차 투표가 마감돼야 열립니다.
                    한 줄 기록은 1~40자(이모지도 한 글자)이고 띄어쓰기만 있으면 안 됩니다. FAILED면 금액 1~999,999원이 필수입니다.
                    방 인원이 2명 이상이면 개인 공양미 10석(riceEarned)을 받고 한 솥에도 10석(potEarned)이 들어갑니다.
                    사진은 업로드 URL로 올린 imageKey를 넣습니다(선택). 내 키가 아니거나 없거나 만료된 키면 다른 확인을 모두 통과한 뒤 COMMON_002(field: imageKey)입니다.
                    """)
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "등록 성공"),
            @ApiResponse(
                    responseCode = "400",
                    description = "COMMON_002 입력값 오류 (field: result, memo, spentAmount, imageKey, isImageKept)",
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
        // 회차는 요청을 받은 시각으로 정해서, 사진을 옮기기 전에 읽는다 (CERT-01 7번).
        LocalDateTime requestedAt = LocalDateTime.now(clock);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(Response.created("인증했습니다.", certificationPhotoService.withPhoto(userId, request.imageKey(),
                        photo -> certificationService.create(userId, roomId, request, photo, requestedAt))));
    }

    @PatchMapping("/certifications/{certificationId}")
    @Operation(summary = "인증 수정 (CERT-02)",
            description = """
                    등록 후 10분 안에, 투표가 시작되기 전까지 수정합니다. 등록처럼 모든 값을 보내 통째로 바꾸고 공양미를 다시 주지 않습니다.
                    ENDURED로 바꾸면 저장된 금액을 지웁니다. 투표가 열리지 않은 회차는 10분까지 수정할 수 있습니다.
                    사진은 imageKey(새 사진)와 isImageKept(필수)로 정합니다. 새 사진이면 바꾸고, imageKey 없이 true면 유지, false면 지웁니다.
                    바꾸거나 지운 기존 사진은 바로 삭제합니다. imageKey와 isImageKept: true를 함께 보내면 COMMON_002입니다.
                    확인 순서: 입력값(COMMON_002) → 없거나 삭제된 인증(CERT_002) → 본인 인증 아님(CERT_003) → 수정 시간 지남(CERT_004) → 투표 시작(CERT_005) → 사진 키(COMMON_002).
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
                certificationPhotoService.withPhoto(userId, request.imageKey(),
                        photo -> certificationService.update(userId, certificationId, request, photo))));
    }

    @DeleteMapping("/certifications/{certificationId}")
    @Operation(summary = "인증 삭제 (CERT-02)",
            description = """
                    등록 후 10분 안에, 투표가 시작되기 전까지 삭제합니다. 그 인증으로 받은 개인 공양미를 회수하고, 같은 회차에 다시 인증할 수 있습니다.
                    이미 삭제한 내 인증을 다시 삭제하면 회수 없이(riceRecalled: 0) 성공합니다. 이 확인은 잠금 검사보다 먼저 합니다.
                    확인 순서: 없는 인증(CERT_002) → 본인 인증 아님(CERT_003) → 이미 삭제됨(성공) → 수정 시간 지남(CERT_004) → 투표 시작(CERT_005).
                    그 인증이 들어간 솥이 진행 중이면 한 솥에서도 뺍니다(potRecalled). 이미 가득 찬 솥이면 0입니다. 사진도 바로 지웁니다.
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
