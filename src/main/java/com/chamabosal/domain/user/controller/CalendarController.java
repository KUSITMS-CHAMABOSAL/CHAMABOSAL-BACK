package com.chamabosal.domain.user.controller;

import com.chamabosal.domain.user.dto.response.CalendarResponse;
import com.chamabosal.domain.user.service.CalendarService;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 경로가 방 아래라 UserController(`/api/users/me`)와 나눴다. Swagger에서는 같은 사용자 묶음으로 보인다. */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api")
@Tag(name = "[USER] 사용자 API")
public class CalendarController {

    private final CalendarService calendarService;

    @GetMapping("/rooms/{roomId}/members/me/calendar")
    @Operation(summary = "인증 캘린더 (USER-02)",
            description = """
                    내 보살 탭의 방별 인증 캘린더입니다. 이 방의 내 인증만, 인증한 날만 날짜 순으로 줍니다. 삭제한 인증은 빠집니다.
                    회차 날짜 기준이라 03:00 전 인증은 전날로 들어갑니다. month(yyyy-MM)를 비우면 지금 회차 날짜의 달입니다.
                    """)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공"),
            @ApiResponse(responseCode = "400", description = "COMMON_002 월 형식 오류 (field: month)",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "COMMON_008 로그인 필요",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "ROOM_002 이 방 멤버가 아님",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "ROOM_005 없거나 삭제된 방",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<Response<CalendarResponse>> getCalendar(
            @Parameter(hidden = true) @AuthenticationPrincipal Long userId,
            @PathVariable Long roomId,
            @Parameter(description = "yyyy-MM. 비우면 지금 회차 날짜의 달", example = "2026-11")
            @RequestParam(required = false) String month
    ) {
        return ResponseEntity.ok(Response.ok(calendarService.getCalendar(userId, roomId, month)));
    }
}
