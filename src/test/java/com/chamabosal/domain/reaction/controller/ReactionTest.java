package com.chamabosal.domain.reaction.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.chamabosal.domain.certification.repository.CertificationRepository;
import com.chamabosal.domain.pot.repository.PotContributionRepository;
import com.chamabosal.domain.pot.repository.RoomPotRepository;
import com.chamabosal.domain.reaction.entity.Reaction;
import com.chamabosal.domain.reaction.entity.ReactionType;
import com.chamabosal.domain.reaction.repository.ReactionRepository;
import com.chamabosal.domain.rice.entity.RiceHistory;
import com.chamabosal.domain.rice.entity.RiceReason;
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

/** 인증 리액션 (REACT-01). 공통 Given: 방에 A, B, C가 있고 인증 시간이며 B가 인증했다. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(MutableClock.Config.class)
class ReactionTest {

    private static final LocalDate DEC_1 = LocalDate.of(2026, 12, 1);
    private static final LocalDateTime T0 = DEC_1.atTime(20, 0);

    @Autowired MockMvc mockMvc;
    @Autowired MutableClock clock;
    @Autowired JwtProvider jwtProvider;
    @Autowired ReactionRepository reactionRepository;
    @Autowired RiceHistoryRepository riceHistoryRepository;
    @Autowired RoundParticipantRepository roundParticipantRepository;
    @Autowired CertificationRepository certificationRepository;
    @Autowired RoundRepository roundRepository;
    @Autowired PotContributionRepository potContributionRepository;
    @Autowired RoomPotRepository roomPotRepository;
    @Autowired RoomMemberRepository roomMemberRepository;
    @Autowired RoomRepository roomRepository;
    @Autowired UserRepository userRepository;

    private int userSequence;
    private Room room;
    private User userA;
    private User userB;
    private User userC;
    private Long certificationB;

    @BeforeEach
    void setUp() throws Exception {
        clock.set(T0);
        room = roomRepository.save(Room.create("배달 끊기 방", RoomGoal.DELIVERY, 4, 300));
        userA = newUser();
        userB = newUser();
        userC = newUser();
        addMember(userA, "민지");
        addMember(userB, "지수");
        addMember(userC, "하린");
        certificationB = certify(userB);
    }

    @AfterEach
    void tearDown() {
        reactionRepository.deleteAll();
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
    void REACT_01_1_리액션을_남긴다() throws Exception {
        react(userA, certificationB, "GOOD").andExpect(status().isOk());

        assertThat(countOf(certificationB, ReactionType.GOOD)).isEqualTo(1);
    }

    @Test
    void REACT_01_2_다른_리액션을_누르면_바꾼다() throws Exception {
        react(userA, certificationB, "GOOD").andExpect(status().isOk());

        react(userA, certificationB, "BAD").andExpect(status().isOk());

        assertThat(countOf(certificationB, ReactionType.GOOD)).isZero();
        assertThat(countOf(certificationB, ReactionType.BAD)).isEqualTo(1);
    }

    @Test
    void REACT_01_3_같은_리액션을_다시_누르면_취소한다() throws Exception {
        react(userA, certificationB, "GOOD").andExpect(status().isOk());

        cancel(userA, certificationB).andExpect(status().isOk());

        assertThat(countOf(certificationB, ReactionType.GOOD)).isZero();
    }

    @Test
    void REACT_01_4_본인_인증에는_남길_수_없다() throws Exception {
        Long mine = certify(userA);

        react(userA, mine, "GOOD").andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("REACT_001"));
    }

    @Test
    void REACT_01_5_투표가_시작되면_남길_수_없다() throws Exception {
        certify(userA);
        certify(userC);

        react(userA, certificationB, "GOOD").andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("REACT_002"));
        assertThat(reactionRepository.findAll()).isEmpty();
    }

    @Test
    void REACT_01_8_본인_인증이면서_투표_시작_후면_REACT_001() throws Exception {
        Long mine = certify(userA);
        certify(userC);

        react(userA, mine, "GOOD").andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("REACT_001"));
    }

    @Test
    void REACT_01_9_투표_시작_뒤_남긴_리액션_없이_취소하면_성공한다() throws Exception {
        certify(userA);
        certify(userC);

        cancel(userA, certificationB).andExpect(status().isOk());
    }

    @Test
    void REACT_01_10_처음_남기면_개인과_방_솥에_1석씩_적립한다() throws Exception {
        int potBefore = potAmount();

        react(userA, certificationB, "GOOD")
                .andExpect(jsonPath("$.data.riceEarned").value(1))
                .andExpect(jsonPath("$.data.potEarned").value(1));

        assertThat(reactionRewards(userA)).extracting(RiceHistory::getDelta).containsExactly(1);
        assertThat(potAmount()).isEqualTo(potBefore + 1);
    }

    @Test
    void REACT_01_11_바꾸거나_취소하고_다시_남겨도_보상은_한_번이고_회수도_없다() throws Exception {
        react(userA, certificationB, "GOOD").andExpect(jsonPath("$.data.riceEarned").value(1));
        int potAfterFirst = potAmount();

        react(userA, certificationB, "BAD").andExpect(jsonPath("$.data.riceEarned").value(0));
        cancel(userA, certificationB).andExpect(status().isOk());
        react(userA, certificationB, "GOOD")
                .andExpect(jsonPath("$.data.riceEarned").value(0))
                .andExpect(jsonPath("$.data.potEarned").value(0));

        assertThat(reactionRewards(userA)).extracting(RiceHistory::getDelta).containsExactly(1);
        assertThat(riceHistoryRepository.sumDeltaByUserId(userA.getId())).isEqualTo(1);
        assertThat(potAmount()).isEqualTo(potAfterFirst);
    }

    private ResultActions react(User user, Long certificationId, String type) throws Exception {
        return mockMvc.perform(put("/api/certifications/{certificationId}/reactions/me", certificationId)
                .header("Authorization", bearer(user))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"type\": \"" + type + "\"}"));
    }

    private ResultActions cancel(User user, Long certificationId) throws Exception {
        return mockMvc.perform(delete("/api/certifications/{certificationId}/reactions/me", certificationId)
                .header("Authorization", bearer(user)));
    }

    private Long certify(User user) throws Exception {
        String content = mockMvc.perform(post("/api/rooms/{roomId}/certifications", room.getId())
                        .header("Authorization", bearer(user))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"result": "ENDURED", "memo": "집밥 해먹었어요"}
                                """))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(content, "$.data.certificationId")).longValue();
    }

    private long countOf(Long certificationId, ReactionType type) {
        return reactionRepository.findAll().stream()
                .filter(r -> r.getCertification().getId().equals(certificationId))
                .map(Reaction::getType)
                .filter(type::equals)
                .count();
    }

    private List<RiceHistory> reactionRewards(User user) {
        return riceHistoryRepository.findAll().stream()
                .filter(h -> h.getUser().getId().equals(user.getId()) && h.getReason() == RiceReason.REACTION)
                .toList();
    }

    private int potAmount() {
        return roomPotRepository.findFirstByRoom_IdOrderBySeqDesc(room.getId()).orElseThrow().getAmount();
    }

    private String bearer(User user) {
        return "Bearer " + jwtProvider.createAccessToken(user.getId());
    }

    private User newUser() {
        return userRepository.save(User.create("anon-react-" + userSequence++));
    }

    private RoomMember addMember(User user, String nickname) {
        return roomMemberRepository.save(RoomMember.create(room, user, nickname, BosalColor.PINK, DEC_1.minusDays(10).atStartOfDay()));
    }
}
