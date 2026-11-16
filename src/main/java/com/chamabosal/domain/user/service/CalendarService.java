package com.chamabosal.domain.user.service;

import com.chamabosal.domain.certification.repository.CertificationRepository;
import com.chamabosal.domain.room.entity.Room;
import com.chamabosal.domain.room.entity.RoomMember;
import com.chamabosal.domain.room.repository.RoomMemberRepository;
import com.chamabosal.domain.room.repository.RoomRepository;
import com.chamabosal.domain.round.entity.Round;
import com.chamabosal.domain.user.dto.response.CalendarResponse;
import com.chamabosal.global.common.exception.BaseErrorCode;
import com.chamabosal.global.common.exception.BaseException;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 내 보살 탭의 방별 인증 캘린더 (USER-02). */
@Service
@RequiredArgsConstructor
public class CalendarService {

    private static final DateTimeFormatter MONTH_FORMAT = DateTimeFormatter.ofPattern("uuuu-MM");
    /** uuuu는 `+10000-11`처럼 부호 붙은 다섯 자리 연도도 읽어서, 파싱 전에 yyyy-MM 모양부터 확인한다 (코드리뷰 반영). */
    private static final Pattern MONTH_PATTERN = Pattern.compile("[0-9]{4}-[0-9]{2}");
    private static final String MONTH_MESSAGE = "월은 yyyy-MM 형식이에요.";

    private final RoomRepository roomRepository;
    private final RoomMemberRepository roomMemberRepository;
    private final CertificationRepository certificationRepository;
    private final Clock clock;

    /**
     * 멤버 행이 방마다 따로라 그 행의 인증만 읽으면 이 방의 내 인증이다. 다시 들어온 멤버는 같은 행을 써서 나가기 전 인증도 보인다.
     * 월은 회차 날짜 기준이고, 비우면 지금 회차 날짜의 달이다 (3번).
     */
    @Transactional(readOnly = true)
    public CalendarResponse getCalendar(Long userId, Long roomId, String month) {
        YearMonth target = parseMonth(month);
        Room room = roomRepository.findById(roomId)
                .filter(found -> !found.isDeleted())
                .orElseThrow(() -> new BaseException(BaseErrorCode.ROOM_NOT_FOUND));
        RoomMember me = roomMemberRepository.findByRoom_IdAndUser_IdAndLeftAtIsNull(room.getId(), userId)
                .orElseThrow(() -> new BaseException(BaseErrorCode.NOT_ROOM_MEMBER));
        return CalendarResponse.of(target, certificationRepository.findCalendar(
                me.getId(), target.atDay(1), target.atEndOfMonth()));
    }

    private YearMonth parseMonth(String month) {
        if (month == null || month.isBlank()) {
            return YearMonth.from(Round.dateOf(LocalDateTime.now(clock)));
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
