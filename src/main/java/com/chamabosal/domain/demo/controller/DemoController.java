package com.chamabosal.domain.demo.controller;

import com.chamabosal.domain.demo.dto.response.DemoResetResponse;
import com.chamabosal.domain.demo.service.DemoResetService;
import com.chamabosal.global.common.response.ErrorResponse;
import com.chamabosal.global.common.response.Response;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/demo")
@Tag(name = "[DEMO] 전시 시연 API", description = "전시 시연용. 시연 설정이 켜져 있을 때만 동작")
public class DemoController {

    private final DemoResetService demoResetService;

    @PostMapping("/reset")
    @SecurityRequirements
    @Operation(
            summary = "시연 리셋",
            description = "시연 번호의 시연 계정을 방문객 맞이 전 상태로 되돌리고 시연 방 2개를 새로 만듭니다. "
                    + "방문객이 폰을 반납한 뒤 실행합니다. (DEMO-01)"
    )
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "리셋 성공"),
            @ApiResponse(
                    responseCode = "401",
                    description = "시연 키 불일치 (COMMON_008)",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "시연 설정 꺼짐 (COMMON_001), 설정에 없는 시연 번호 (DEMO_001)",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))
            ),
            @ApiResponse(
                    responseCode = "409",
                    description = "시연 계정이 일반 방에 참여 중 (DEMO_002)",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))
            )
    })
    public ResponseEntity<Response<DemoResetResponse>> reset(
            @Parameter(description = "시연 키") @RequestHeader(value = "X-Demo-Key", required = false) String demoKey,
            @Parameter(description = "시연 번호 (폰마다 1, 2, …)", example = "1") @RequestParam int slot
    ) {
        DemoResetResponse response = demoResetService.reset(demoKey, slot);
        return ResponseEntity.status(HttpStatus.CREATED).body(Response.created("시연을 리셋했어요.", response));
    }
}
