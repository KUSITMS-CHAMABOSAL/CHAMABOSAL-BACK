package com.chamabosal.domain.user.dto.response;

import com.chamabosal.domain.certification.entity.CertificationResult;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;
import java.util.List;

/** 내 보살 이번 달 기록 (USER-02). 참여한 방을 합쳐 나 기준이다. */
public record MyCalendarResponse(
        @Schema(description = "조회한 달 (yyyy-MM)", example = "2026-11") String month,
        @Schema(description = "그 달 참았어요 인증 수, 방 합산") int enduredCount,
        @Schema(description = "그 달 지목된 횟수, 방 합산. 방어 성공은 세지 않음") int pickedCount,
        @Schema(description = "하나라도 인증한 날이 이어진 일수. 월과 상관없이 지금 기준이고, 오늘 아직 인증 전이면 어제까지") int streakDays,
        @Schema(description = "인증한 날만, 날짜 순. 그날 하나라도 못 참았으면 FAILED") List<CalendarDay> calendar
) {

    public record CalendarDay(@Schema(description = "회차 날짜") LocalDate date, CertificationResult result) {
    }
}
