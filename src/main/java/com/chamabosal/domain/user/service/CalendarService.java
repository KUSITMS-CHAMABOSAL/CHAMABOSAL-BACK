package com.chamabosal.domain.user.service;

import com.chamabosal.domain.certification.entity.CertificationResult;
import com.chamabosal.domain.certification.repository.CertificationRepository;
import com.chamabosal.domain.round.entity.Round;
import com.chamabosal.domain.round.entity.RoundResultType;
import com.chamabosal.domain.round.repository.RoundRepository;
import com.chamabosal.domain.user.dto.response.MyCalendarResponse;
import com.chamabosal.domain.user.dto.response.MyCalendarResponse.CalendarDay;
import com.chamabosal.global.common.exception.BaseErrorCode;
import com.chamabosal.global.common.exception.BaseException;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 내 보살 이번 달 기록 (USER-02). 참여한 방을 합친 캘린더와 이번 달 수치다. */
@Service
@RequiredArgsConstructor
public class CalendarService {

    private static final DateTimeFormatter MONTH_FORMAT = DateTimeFormatter.ofPattern("uuuu-MM");
    /** uuuu는 `+10000-11`처럼 부호 붙은 다섯 자리 연도도 읽어서, 파싱 전에 yyyy-MM 모양부터 확인한다 (코드리뷰 반영). */
    private static final Pattern MONTH_PATTERN = Pattern.compile("[0-9]{4}-[0-9]{2}");
    private static final String MONTH_MESSAGE = "월은 yyyy-MM 형식이에요.";

    private final CertificationRepository certificationRepository;
    private final RoundRepository roundRepository;
    private final Clock clock;

    /**
     * 참여한 방을 합쳐 나 기준으로 돌려준다. 멤버 행이 아니라 사용자로 모아서 지금은 나간 방의 기록도 들어간다 (1·2번, Q-41).
     * 같은 날 두 방 중 하나라도 못 참았으면 그날은 못 참았어요다 (7번). 연속 인증은 월과 상관없이 지금 기준이다 (6번).
     */
    @Transactional(readOnly = true)
    public MyCalendarResponse getCalendar(Long userId, String month) {
        // 지금 회차 날짜는 한 번만 구해 기본 월과 연속 인증이 같은 날을 기준으로 삼게 한다 (코드리뷰 반영).
        LocalDate today = Round.dateOf(LocalDateTime.now(clock));
        YearMonth target = parseMonth(month, today);
        Map<LocalDate, CertificationResult> byDate = new TreeMap<>();
        int enduredCount = 0;
        for (Object[] row : certificationRepository.findMyResultsInPeriod(userId, target.atDay(1), target.atEndOfMonth())) {
            LocalDate date = (LocalDate) row[0];
            CertificationResult result = (CertificationResult) row[1];
            if (result == CertificationResult.ENDURED) {
                enduredCount++;
            }
            byDate.merge(date, result, (kept, added) ->
                    kept == CertificationResult.FAILED || added == CertificationResult.FAILED
                            ? CertificationResult.FAILED : CertificationResult.ENDURED);
        }
        int pickedCount = (int) roundRepository.countByPickedUser_IdAndResultTypeAndRoundDateBetween(
                userId, RoundResultType.PICKED, target.atDay(1), target.atEndOfMonth());
        List<CalendarDay> calendar = byDate.entrySet().stream()
                .map(entry -> new CalendarDay(entry.getKey(), entry.getValue()))
                .toList();
        return new MyCalendarResponse(target.toString(), enduredCount, pickedCount, streakDays(userId, today), calendar);
    }

    /** 오늘(지금 회차 날짜)에 인증했으면 오늘부터, 아니면 어제부터 하루씩 거슬러 이어진 날을 센다 (6번, Q-42). */
    private int streakDays(Long userId, LocalDate today) {
        List<LocalDate> dates = certificationRepository.findMyCertifiedDatesUntil(userId, today);
        LocalDate expected = !dates.isEmpty() && dates.get(0).equals(today) ? today : today.minusDays(1);
        int streak = 0;
        for (LocalDate date : dates) {
            if (!date.equals(expected)) {
                break;
            }
            streak++;
            expected = expected.minusDays(1);
        }
        return streak;
    }

    private YearMonth parseMonth(String month, LocalDate today) {
        if (month == null || month.isBlank()) {
            return YearMonth.from(today);
        }
        if (!MONTH_PATTERN.matcher(month).matches()) {
            throw BaseException.withField(BaseErrorCode.INVALID_REQUEST, MONTH_MESSAGE, "month");
        }
        try {
            return YearMonth.parse(month, MONTH_FORMAT);
        } catch (DateTimeParseException e) {
            throw BaseException.withField(BaseErrorCode.INVALID_REQUEST, MONTH_MESSAGE, "month");
        }
    }
}
