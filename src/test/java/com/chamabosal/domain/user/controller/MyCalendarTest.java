package com.chamabosal.domain.user.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.chamabosal.domain.certification.entity.Certification;
import com.chamabosal.domain.certification.entity.CertificationResult;
import com.chamabosal.domain.certification.repository.CertificationRepository;
import com.chamabosal.domain.pot.repository.PotContributionRepository;
import com.chamabosal.domain.pot.repository.RoomPotRepository;
import com.chamabosal.domain.rice.repository.RiceHistoryRepository;
import com.chamabosal.domain.room.entity.BosalColor;
import com.chamabosal.domain.room.entity.LeaveType;
import com.chamabosal.domain.room.entity.Room;
import com.chamabosal.domain.room.entity.RoomGoal;
import com.chamabosal.domain.room.entity.RoomMember;
import com.chamabosal.domain.room.repository.RoomMemberRepository;
import com.chamabosal.domain.room.repository.RoomRepository;
import com.chamabosal.domain.round.entity.Round;
import com.chamabosal.domain.round.entity.RoundResultType;
import com.chamabosal.domain.round.repository.RoundParticipantRepository;
import com.chamabosal.domain.round.repository.RoundRepository;
import com.chamabosal.domain.user.entity.User;
import com.chamabosal.domain.user.repository.UserRepository;
import com.chamabosal.global.security.jwt.JwtProvider;
import com.chamabosal.support.MutableClock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/** 내 보살 이번 달 기록 (USER-02). 공통 Given: A가 방 1, 방 2의 멤버이고 지금은 2026-11-11 12:00(11/11 회차)이다. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(MutableClock.Config.class)
class MyCalendarTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 11, 12, 0);
    private static final LocalDate TODAY = NOW.toLocalDate();

    @Autowired MockMvc mockMvc;
    @Autowired MutableClock clock;
    @Autowired JwtProvider jwtProvider;
    @Autowired UserRepository userRepository;
    @Autowired RoomRepository roomRepository;
    @Autowired RoomMemberRepository roomMemberRepository;
    @Autowired PotContributionRepository potContributionRepository;
    @Autowired RoomPotRepository roomPotRepository;
    @Autowired RoundRepository roundRepository;
    @Autowired RoundParticipantRepository roundParticipantRepository;
    @Autowired CertificationRepository certificationRepository;
    @Autowired RiceHistoryRepository riceHistoryRepository;

    private User userA;
    private RoomMember memberA1;
    private RoomMember memberA2;

    @BeforeEach
    void setUp() {
        clock.set(NOW);
        userA = userRepository.save(User.create("anon-records-a"));
        Room room1 = roomRepository.save(Room.create("배달 끊기 방", RoomGoal.DELIVERY, 4, 300));
        Room room2 = roomRepository.save(Room.create("카페 참기 방", RoomGoal.CAFE, 4, 300));
        memberA1 = roomMemberRepository.save(RoomMember.create(room1, userA, "민지", BosalColor.PINK, NOW.minusMonths(2)));
        memberA2 = roomMemberRepository.save(RoomMember.create(room2, userA, "민지", BosalColor.PINK, NOW.minusMonths(2)));
    }

    @AfterEach
    void tearDown() {
        riceHistoryRepository.deleteAll();
        roundParticipantRepository.deleteAll();
        certificationRepository.deleteAll();
        roundRepository.deleteAll();
        potContributionRepository.deleteAll();
        roomPotRepository.deleteAll();
        roomMemberRepository.deleteAll();
        roomRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    void USER_02_1_두_방의_참은_횟수를_합친다() throws Exception {
        certify(memberA1, day(1), CertificationResult.ENDURED);
        certify(memberA1, day(2), CertificationResult.ENDURED);
        certify(memberA2, day(1), CertificationResult.ENDURED);
        certify(memberA2, day(2), CertificationResult.FAILED);

        records(null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.month").value("2026-11"))
                .andExpect(jsonPath("$.data.enduredCount").value(3));
    }

    @Test
    void USER_02_2_같은_날_하나라도_못_참았으면_못_참았어요다() throws Exception {
        certify(memberA1, day(3), CertificationResult.ENDURED);
        certify(memberA2, day(3), CertificationResult.FAILED);
        certify(memberA1, day(4), CertificationResult.ENDURED);
        certify(memberA2, day(4), CertificationResult.ENDURED);

        records(null)
                .andExpect(jsonPath("$.data.calendar.length()").value(2))
                .andExpect(jsonPath("$.data.calendar[0].date").value("2026-11-03"))
                .andExpect(jsonPath("$.data.calendar[0].result").value("FAILED"))
                .andExpect(jsonPath("$.data.calendar[1].date").value("2026-11-04"))
                .andExpect(jsonPath("$.data.calendar[1].result").value("ENDURED"));
    }

    @Test
    void USER_02_3_나간_방의_기록도_들어간다() throws Exception {
        certify(memberA1, day(5), CertificationResult.ENDURED);
        memberA1.leave(NOW.minusDays(1), LeaveType.LEFT);
        roomMemberRepository.save(memberA1);

        records(null)
                .andExpect(jsonPath("$.data.enduredCount").value(1))
                .andExpect(jsonPath("$.data.calendar[0].date").value("2026-11-05"));
    }

    @Test
    void USER_02_4_삭제한_인증은_넣지_않는다() throws Exception {
        Certification deleted = certify(memberA1, day(5), CertificationResult.ENDURED);
        deleted.delete(day(5).atTime(20, 5));
        certificationRepository.save(deleted);

        records(null)
                .andExpect(jsonPath("$.data.enduredCount").value(0))
                .andExpect(jsonPath("$.data.calendar.length()").value(0));
    }

    @Test
    void USER_02_5_03시_전_인증은_전날_회차로_들어간다() throws Exception {
        clock.set(LocalDateTime.of(2026, 12, 1, 2, 40));
        certify(memberA1, LocalDate.of(2026, 11, 30), CertificationResult.ENDURED);

        records("2026-11")
                .andExpect(jsonPath("$.data.calendar[0].date").value("2026-11-30"))
                .andExpect(jsonPath("$.data.enduredCount").value(1));
    }

    @Test
    void USER_02_6_두_방에서_이어진_인증은_연속_인증이다() throws Exception {
        certify(memberA1, TODAY.minusDays(2), CertificationResult.ENDURED);
        certify(memberA1, TODAY.minusDays(1), CertificationResult.FAILED);
        certify(memberA2, TODAY, CertificationResult.ENDURED);

        records(null).andExpect(jsonPath("$.data.streakDays").value(3));
    }

    @Test
    void USER_02_7_오늘_아직_인증하지_않았으면_어제까지_일수다() throws Exception {
        certify(memberA1, TODAY.minusDays(2), CertificationResult.ENDURED);
        certify(memberA2, TODAY.minusDays(1), CertificationResult.ENDURED);

        records(null).andExpect(jsonPath("$.data.streakDays").value(2));
    }

    @Test
    void USER_02_8_하루라도_빠지면_그_뒤부터_다시_센다() throws Exception {
        certify(memberA1, TODAY.minusDays(3), CertificationResult.ENDURED);
        certify(memberA1, TODAY.minusDays(1), CertificationResult.ENDURED);
        certify(memberA1, TODAY, CertificationResult.ENDURED);

        records(null).andExpect(jsonPath("$.data.streakDays").value(2));
    }

    @Test
    void 어제도_오늘도_인증하지_않았으면_연속_인증은_0이다() throws Exception {
        certify(memberA1, TODAY.minusDays(2), CertificationResult.ENDURED);

        records(null).andExpect(jsonPath("$.data.streakDays").value(0));
    }

    @Test
    void 연속_인증은_월이_바뀌어도_이어진다() throws Exception {
        clock.set(LocalDateTime.of(2026, 12, 2, 12, 0));
        certify(memberA1, LocalDate.of(2026, 11, 30), CertificationResult.ENDURED);
        certify(memberA1, LocalDate.of(2026, 12, 1), CertificationResult.ENDURED);
        certify(memberA2, LocalDate.of(2026, 12, 2), CertificationResult.ENDURED);

        records("2026-11").andExpect(jsonPath("$.data.streakDays").value(3));
    }

    @Test
    void USER_02_9_두_방의_지목_횟수를_합치고_방어_성공은_세지_않는다() throws Exception {
        announce(memberA1, day(1), RoundResultType.PICKED);
        announce(memberA1, day(2), RoundResultType.PICKED);
        announce(memberA2, day(3), RoundResultType.PICKED);
        announce(memberA2, day(4), RoundResultType.DEFENDED);
        announce(memberA1, LocalDate.of(2026, 10, 31), RoundResultType.PICKED);

        records(null).andExpect(jsonPath("$.data.pickedCount").value(3));
    }

    @Test
    void USER_02_10_지난_달을_조회할_수_있다() throws Exception {
        certify(memberA1, LocalDate.of(2026, 10, 31), CertificationResult.ENDURED);
        certify(memberA1, day(1), CertificationResult.ENDURED);

        records("2026-10")
                .andExpect(jsonPath("$.data.month").value("2026-10"))
                .andExpect(jsonPath("$.data.enduredCount").value(1))
                .andExpect(jsonPath("$.data.calendar.length()").value(1))
                .andExpect(jsonPath("$.data.calendar[0].date").value("2026-10-31"));
    }

    @Test
    void 다른_사람의_기록은_넣지_않는다() throws Exception {
        User userB = userRepository.save(User.create("anon-records-b"));
        RoomMember memberB1 = roomMemberRepository.save(RoomMember.create(
                memberA1.getRoom(), userB, "지수", BosalColor.MINT, NOW.minusMonths(2)));
        certify(memberB1, day(1), CertificationResult.ENDURED);
        announce(memberB1, day(1), RoundResultType.PICKED);

        records(null)
                .andExpect(jsonPath("$.data.enduredCount").value(0))
                .andExpect(jsonPath("$.data.pickedCount").value(0))
                .andExpect(jsonPath("$.data.calendar.length()").value(0));
    }

    @Test
    void 월_형식이_틀리면_COMMON_002를_돌려준다() throws Exception {
        for (String month : new String[]{"2026-13", "202611", "+10000-11"}) {
            records(month)
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("COMMON_002"))
                    .andExpect(jsonPath("$.field").value("month"));
        }
    }

    private static LocalDate day(int dayOfMonth) {
        return LocalDate.of(2026, 11, dayOfMonth);
    }

    private Round roundOf(RoomMember member, LocalDate roundDate) {
        return roundRepository.findByRoom_IdAndRoundDate(member.getRoom().getId(), roundDate)
                .orElseGet(() -> roundRepository.save(Round.create(member.getRoom(), roundDate)));
    }

    private Certification certify(RoomMember member, LocalDate roundDate, CertificationResult result) {
        return certificationRepository.save(Certification.create(roundOf(member, roundDate), member, result, "한 줄",
                result == CertificationResult.FAILED ? 12000 : null, roundDate.atTime(20, 0)));
    }

    private void announce(RoomMember picked, LocalDate roundDate, RoundResultType resultType) {
        Round round = roundOf(picked, roundDate);
        round.announce(roundDate.plusDays(1).atTime(15, 0), resultType, picked);
        roundRepository.save(round);
    }

    private ResultActions records(String month) throws Exception {
        var request = get("/api/users/me/calendar").header("Authorization", "Bearer " + jwtProvider.createAccessToken(userA.getId()));
        if (month != null) {
            request.param("month", month);
        }
        return mockMvc.perform(request);
    }
}
