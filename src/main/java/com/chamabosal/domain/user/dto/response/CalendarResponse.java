package com.chamabosal.domain.user.dto.response;

import com.chamabosal.domain.certification.entity.Certification;
import com.chamabosal.domain.certification.entity.CertificationResult;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

/** 인증 캘린더 (USER-02). 인증이 없는 날은 넣지 않는다. */
public record CalendarResponse(
        @Schema(description = "조회한 달 (yyyy-MM)", example = "2026-11") String month,
        @Schema(description = "인증한 날만, 날짜 순") List<CalendarDay> calendar
) {

    public record CalendarDay(@Schema(description = "회차 날짜") LocalDate date, CertificationResult result) {

        static CalendarDay from(Certification certification) {
            return new CalendarDay(certification.getRound().getRoundDate(), certification.getResult());
        }
    }

    public static CalendarResponse of(YearMonth month, List<Certification> certifications) {
        return new CalendarResponse(month.toString(), certifications.stream().map(CalendarDay::from).toList());
    }
}
