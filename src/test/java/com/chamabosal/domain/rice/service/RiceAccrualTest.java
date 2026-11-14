package com.chamabosal.domain.rice.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.chamabosal.domain.certification.repository.CertificationRepository;
import com.chamabosal.domain.rice.entity.RiceHistory;
import com.chamabosal.domain.rice.entity.RiceReason;
import com.chamabosal.domain.rice.entity.RiceRefType;
import com.chamabosal.domain.rice.repository.RiceHistoryRepository;
import com.chamabosal.domain.room.entity.BosalColor;
import com.chamabosal.domain.room.entity.Room;
import com.chamabosal.domain.room.entity.RoomGoal;
import com.chamabosal.domain.room.entity.RoomMember;
import com.chamabosal.domain.room.repository.RoomMemberRepository;
import com.chamabosal.domain.room.repository.RoomRepository;
import com.chamabosal.domain.round.repository.RoundParticipantRepository;
import com.chamabosal.domain.round.repository.RoundRepository;
import com.chamabosal.domain.user.entity.User;
import com.chamabosal.domain.user.repository.UserRepository;
import com.chamabosal.domain.vote.repository.VoteRepository;
import com.chamabosal.global.security.jwt.JwtProvider;
import com.chamabosal.support.MutableClock;
import com.jayway.jsonpath.JsonPath;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
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
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** 개인 공양미 적립 (RICE-01). 공통 Given: 방 인원은 A, B, C 3명이다. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(MutableClock.Config.class)
class RiceAccrualTest {

    private static final LocalDate DEC_1 = LocalDate.of(2026, 12, 1);
    private static final LocalDateTime T0 = DEC_1.atTime(20, 0);

    @Autowired
    MockMvc mockMvc;

    @Autowired
    MutableClock clock;

    @Autowired
    JwtProvider jwtProvider;

    @Autowired
    RiceService riceService;

    @Autowired
    PlatformTransactionManager transactionManager;

    @Autowired
    RiceHistoryRepository riceHistoryRepository;

    @Autowired
    UserRepository userRepository;

    @Autowired
    RoomRepository roomRepository;

    @Autowired
    RoomMemberRepository roomMemberRepository;

    @Autowired
    RoundRepository roundRepository;

    @Autowired
    RoundParticipantRepository roundParticipantRepository;

    @Autowired
    CertificationRepository certificationRepository;

    @Autowired
    VoteRepository voteRepository;

    private int userSequence;
    private Room room;
    private User userA;
    private User userB;
    private User userC;

    @BeforeEach
    void setUp() {
        clock.set(T0);
        room = newRoom();
        userA = newUser();
        userB = newUser();
        userC = newUser();
        addMember(room, userA, "민지");
        addMember(room, userB, "지수");
        addMember(room, userC, "하린");
    }

    @AfterEach
    void tearDown() {
        riceHistoryRepository.deleteAll();
        voteRepository.deleteAll();
        roundParticipantRepository.deleteAll();
        certificationRepository.deleteAll();
        roundRepository.deleteAll();
        roomMemberRepository.deleteAll();
        roomRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    void RICE_01_1_인증하면_10석을_기록한다() throws Exception {
        certifyRequest(userA, room)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.riceEarned").value(10));

        RiceHistory history = only(histories(userA));
        assertThat(history.getDelta()).isEqualTo(10);
        assertThat(history.getReason()).isEqualTo(RiceReason.CERTIFICATION);
        assertThat(history.getRefType()).isEqualTo(RiceRefType.CERTIFICATION);
    }

    @Test
    void RICE_01_2_모두_보살에_투표해도_5석을_기록한다() throws Exception {
        certify(userA, room);
        certify(userB, room);
        certify(userC, room);
        Long roundId = roundRepository.findAll().get(0).getId();

        mockMvc.perform(MockMvcRequestBuilders.post("/api/rounds/{roundId}/votes", roundId)
                        .header("Authorization", bearer(userA))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"targetType": "ALL_BOSAL"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.riceEarned").value(5));

        assertThat(histories(userA)).filteredOn(h -> h.getReason() == RiceReason.VOTE)
                .singleElement().extracting(RiceHistory::getDelta).isEqualTo(5);
    }

    @Test
    void RICE_01_3_혼자인_방에서는_기록하지_않는다() throws Exception {
        Room soloRoom = newRoom();
        User solo = newUser();
        addMember(soloRoom, solo, "혼자");

        certifyRequest(solo, soloRoom)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.riceEarned").value(0));

        assertThat(histories(solo)).isEmpty();
    }

    @Test
    void RICE_01_4_같은_인증_ID로_다시_지급해도_추가로_기록하지_않는다() throws Exception {
        Long certificationId = certify(userA, room);

        int again = new TransactionTemplate(transactionManager).execute(status -> riceService.accrue(
                roomRepository.findById(room.getId()).orElseThrow(), memberOf(userA),
                RiceReason.CERTIFICATION, RiceRefType.CERTIFICATION, certificationId, RiceService.CERTIFICATION_REWARD));

        assertThat(again).isZero();
        assertThat(histories(userA)).hasSize(1);
    }

    @Test
    void RICE_01_5_삭제_후_다시_인증하면_새_인증으로_지급하고_회차_순_지급은_10석이다() throws Exception {
        Long first = certify(userA, room);
        mockMvc.perform(delete("/api/certifications/{certificationId}", first).header("Authorization", bearer(userA)))
                .andExpect(status().isOk());

        Long second = certify(userA, room);

        assertThat(second).isNotEqualTo(first);
        assertThat(histories(userA)).extracting(RiceHistory::getDelta).containsExactlyInAnyOrder(10, -10, 10);
        assertThat(riceHistoryRepository.sumDeltaByUserId(userA.getId())).isEqualTo(10);
    }

    @Test
    void RICE_01_6_장부_합계가_음수면_표시_잔액은_0석이다() {
        riceHistoryRepository.save(RiceHistory.create(userA, room, -10, RiceReason.RECALL, RiceRefType.CERTIFICATION, 1L));

        assertThat(riceHistoryRepository.sumDeltaByUserId(userA.getId())).isEqualTo(-10);
        assertThat(riceService.getDisplayBalance(userA.getId())).isZero();
    }

    @Test
    void RICE_01_12_적립_기록에_인증한_방을_남긴다() throws Exception {
        Room otherRoom = newRoom();
        addMember(otherRoom, userA, "민지");
        addMember(otherRoom, newUser(), "도윤");

        certify(userA, room);

        assertThat(only(histories(userA)).getRoom().getId()).isEqualTo(room.getId());
    }

    private Long certify(User user, Room target) throws Exception {
        String content = certifyRequest(user, target).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(content, "$.data.certificationId")).longValue();
    }

    private ResultActions certifyRequest(User user, Room target) throws Exception {
        return mockMvc.perform(MockMvcRequestBuilders.post("/api/rooms/{roomId}/certifications", target.getId())
                .header("Authorization", bearer(user))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"result": "ENDURED", "memo": "집밥 해먹었어요"}
                        """));
    }

    private String bearer(User user) {
        return "Bearer " + jwtProvider.createAccessToken(user.getId());
    }

    private List<RiceHistory> histories(User user) {
        return riceHistoryRepository.findAll().stream()
                .filter(h -> h.getUser().getId().equals(user.getId()))
                .toList();
    }

    private static RiceHistory only(List<RiceHistory> histories) {
        assertThat(histories).hasSize(1);
        return histories.get(0);
    }

    private RoomMember memberOf(User user) {
        return roomMemberRepository.findByRoom_IdAndUser_IdAndLeftAtIsNull(room.getId(), user.getId()).orElseThrow();
    }

    private Room newRoom() {
        return roomRepository.save(Room.create("배달 끊기 방", RoomGoal.DELIVERY, 4));
    }

    private User newUser() {
        return userRepository.save(User.create("anon-rice-" + userSequence++));
    }

    private void addMember(Room target, User user, String nickname) {
        roomMemberRepository.save(RoomMember.create(target, user, nickname, BosalColor.PINK, DEC_1.minusDays(10).atStartOfDay()));
    }
}
