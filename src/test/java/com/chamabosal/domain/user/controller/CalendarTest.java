package com.chamabosal.domain.user.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
import com.chamabosal.domain.round.repository.RoundParticipantRepository;
import com.chamabosal.domain.round.repository.RoundRepository;
import com.chamabosal.domain.user.entity.User;
import com.chamabosal.domain.user.repository.UserRepository;
import com.chamabosal.global.security.jwt.JwtProvider;
import com.chamabosal.support.MutableClock;
import com.jayway.jsonpath.JsonPath;
import java.time.LocalDate;
import java.time.LocalDateTime;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/** 공통 Given: A가 방 1, 방 2의 멤버이고 지금은 2026-11-20 12:00이다. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(MutableClock.Config.class)
class CalendarTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 20, 12, 0);

    @Autowired
    MockMvc mockMvc;

    @Autowired
    MutableClock clock;

    @Autowired
    JwtProvider jwtProvider;

    @Autowired
    UserRepository userRepository;

    @Autowired
    RoomRepository roomRepository;

    @Autowired
    RoomMemberRepository roomMemberRepository;

    @Autowired
    PotContributionRepository potContributionRepository;

    @Autowired
    RoomPotRepository roomPotRepository;

    @Autowired
    RoundRepository roundRepository;

    @Autowired
    RoundParticipantRepository roundParticipantRepository;

    @Autowired
    CertificationRepository certificationRepository;

    @Autowired
    RiceHistoryRepository riceHistoryRepository;

    private User userA;
    private Room room1;
    private Room room2;
    private RoomMember memberA1;
    private RoomMember memberA2;

    @BeforeEach
    void setUp() {
        clock.set(NOW);
        userA = userRepository.save(User.create("anon-a"));
        room1 = roomRepository.save(Room.create("배달 끊기 방", RoomGoal.DELIVERY, 4, 300));
        room2 = roomRepository.save(Room.create("카페 참기 방", RoomGoal.CAFE, 4, 300));
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
    void USER_02_1_월이_없으면_이번_달_인증_날짜와_결과를_돌려준다() throws Exception {
        certify(memberA1, LocalDate.of(2026, 11, 1), CertificationResult.ENDURED);
        certify(memberA1, LocalDate.of(2026, 11, 2), CertificationResult.FAILED);
        RoomMember memberB1 = roomMemberRepository.save(RoomMember.create(
                room1, userRepository.save(User.create("anon-b")), "지수", BosalColor.MINT, NOW.minusMonths(2)));
        certify(memberB1, LocalDate.of(2026, 11, 3), CertificationResult.ENDURED);

        calendar(room1, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.month").value("2026-11"))
                .andExpect(jsonPath("$.data.calendar.length()").value(2))
                .andExpect(jsonPath("$.data.calendar[0].date").value("2026-11-01"))
                .andExpect(jsonPath("$.data.calendar[0].result").value("ENDURED"))
                .andExpect(jsonPath("$.data.calendar[1].date").value("2026-11-02"))
                .andExpect(jsonPath("$.data.calendar[1].result").value("FAILED"));
    }

    @Test
    void USER_02_2_다른_방_인증은_넣지_않는다() throws Exception {
        certify(memberA2, LocalDate.of(2026, 11, 3), CertificationResult.ENDURED);

        calendar(room1, "2026-11")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.calendar.length()").value(0));
    }

    @Test
    void USER_02_3_삭제한_인증은_넣지_않는다() throws Exception {
        clock.set(LocalDateTime.of(2026, 11, 5, 20, 0));
        String body = mockMvc.perform(post("/api/rooms/{roomId}/certifications", room1.getId())
                        .header("Authorization", bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"result\":\"ENDURED\",\"memo\":\"집밥 해먹었어요\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        long certificationId = ((Number) JsonPath.read(body, "$.data.certificationId")).longValue();
        clock.set(LocalDateTime.of(2026, 11, 5, 20, 5));
        mockMvc.perform(delete("/api/certifications/{certificationId}", certificationId).header("Authorization", bearer()))
                .andExpect(status().isOk());

        calendar(room1, "2026-11")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.calendar.length()").value(0));
    }

    @Test
    void USER_02_4_지난_달을_조회할_수_있다() throws Exception {
        certify(memberA1, LocalDate.of(2026, 10, 31), CertificationResult.FAILED);
        certify(memberA1, LocalDate.of(2026, 11, 1), CertificationResult.ENDURED);

        calendar(room1, "2026-10")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.month").value("2026-10"))
                .andExpect(jsonPath("$.data.calendar.length()").value(1))
                .andExpect(jsonPath("$.data.calendar[0].date").value("2026-10-31"))
                .andExpect(jsonPath("$.data.calendar[0].result").value("FAILED"));
    }

    @Test
    void USER_02_5_03시_전_인증은_전날_회차로_들어간다() throws Exception {
        clock.set(LocalDateTime.of(2026, 12, 1, 2, 30));
        mockMvc.perform(post("/api/rooms/{roomId}/certifications", room1.getId())
                        .header("Authorization", bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"result\":\"ENDURED\",\"memo\":\"집밥 해먹었어요\"}"))
                .andExpect(status().isCreated());

        calendar(room1, "2026-11")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.calendar.length()").value(1))
                .andExpect(jsonPath("$.data.calendar[0].date").value("2026-11-30"));
    }

    @Test
    void 새벽_3시부터는_그날_회차이고_월을_비우면_그_달이다() throws Exception {
        clock.set(LocalDateTime.of(2026, 12, 1, 3, 0));
        mockMvc.perform(post("/api/rooms/{roomId}/certifications", room1.getId())
                        .header("Authorization", bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"result\":\"ENDURED\",\"memo\":\"집밥 해먹었어요\"}"))
                .andExpect(status().isCreated());

        calendar(room1, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.month").value("2026-12"))
                .andExpect(jsonPath("$.data.calendar.length()").value(1))
                .andExpect(jsonPath("$.data.calendar[0].date").value("2026-12-01"));
    }

    @Test
    void 월이_없거나_비어_있으면_지금_회차_날짜의_달이다() throws Exception {
        certify(memberA1, LocalDate.of(2026, 11, 30), CertificationResult.ENDURED);
        clock.set(LocalDateTime.of(2026, 12, 1, 2, 0));

        calendar(room1, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.month").value("2026-11"))
                .andExpect(jsonPath("$.data.calendar.length()").value(1));
        calendar(room1, "")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.month").value("2026-11"));
    }

    @Test
    void USER_02_6_멤버가_아닌_방이면_ROOM_002를_돌려준다() throws Exception {
        Room room3 = roomRepository.save(Room.create("쇼핑 참기 방", RoomGoal.SHOPPING, 4, 300));

        calendar(room3, "2026-11")
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ROOM_002"));
    }

    @Test
    void 나간_방이면_ROOM_002를_돌려준다() throws Exception {
        memberA1.leave(NOW.minusDays(1), LeaveType.LEFT);
        roomMemberRepository.save(memberA1);

        calendar(room1, "2026-11")
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ROOM_002"));
    }

    @Test
    void 삭제된_방이면_ROOM_005를_돌려준다() throws Exception {
        room1.delete(NOW.minusDays(1));
        roomRepository.save(room1);

        calendar(room1, "2026-11")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ROOM_005"));
    }

    @Test
    void 월_형식이_틀리면_COMMON_002를_돌려준다() throws Exception {
        for (String month : new String[]{"2026-13", "202611", "11월", "+10000-11"}) {
            calendar(room1, month)
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("COMMON_002"))
                    .andExpect(jsonPath("$.field").value("month"));
        }
    }

    private void certify(RoomMember member, LocalDate roundDate, CertificationResult result) {
        Round round = roundRepository.findByRoom_IdAndRoundDate(member.getRoom().getId(), roundDate)
                .orElseGet(() -> roundRepository.save(Round.create(member.getRoom(), roundDate)));
        certificationRepository.save(Certification.create(round, member, result, "한 줄",
                result == CertificationResult.FAILED ? 12000 : null, roundDate.atTime(20, 0)));
    }

    private ResultActions calendar(Room room, String month) throws Exception {
        var request = get("/api/rooms/{roomId}/members/me/calendar", room.getId()).header("Authorization", bearer());
        if (month != null) {
            request.param("month", month);
        }
        return mockMvc.perform(request);
    }

    private String bearer() {
        return "Bearer " + jwtProvider.createAccessToken(userA.getId());
    }
}
