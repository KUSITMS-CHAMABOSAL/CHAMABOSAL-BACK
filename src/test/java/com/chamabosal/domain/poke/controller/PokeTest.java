package com.chamabosal.domain.poke.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.chamabosal.domain.certification.repository.CertificationRepository;
import com.chamabosal.domain.poke.entity.Poke;
import com.chamabosal.domain.poke.repository.PokeRepository;
import com.chamabosal.domain.pot.repository.PotContributionRepository;
import com.chamabosal.domain.pot.repository.RoomPotRepository;
import com.chamabosal.domain.rice.repository.RiceHistoryRepository;
import com.chamabosal.domain.room.entity.BosalColor;
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
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/** 콕 찌르기 (POKE-01). 공통 Given: 방에 A, B, C가 있고 인증 시간이다. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(MutableClock.Config.class)
class PokeTest {

    private static final LocalDate DEC_1 = LocalDate.of(2026, 12, 1);
    private static final LocalDateTime T0 = DEC_1.atTime(20, 0);

    @Autowired MockMvc mockMvc;
    @Autowired MutableClock clock;
    @Autowired JwtProvider jwtProvider;
    @Autowired PokeRepository pokeRepository;
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
    private RoomMember memberA;
    private RoomMember memberB;

    @BeforeEach
    void setUp() {
        clock.set(T0);
        room = newRoom();
        userA = newUser();
        userB = newUser();
        userC = newUser();
        memberA = addMember(room, userA, "민지");
        memberB = addMember(room, userB, "지수");
        addMember(room, userC, "하린");
    }

    @AfterEach
    void tearDown() {
        pokeRepository.deleteAll();
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
    void POKE_01_1_받는_사람_기준으로_누적한다() throws Exception {
        poke(userA, memberB, 3).andExpect(status().isCreated());
        poke(userC, memberB, 2).andExpect(status().isCreated());

        assertThat(receivedCount(memberB)).isEqualTo(5);
        // 같은 사람이 다시 찌르면 그 사람의 행에 더한다 (POKE-01 7번).
        poke(userA, memberB, 4).andExpect(status().isCreated());
        assertThat(receivedCount(memberB)).isEqualTo(9);
        assertThat(pokeRepository.findAll()).hasSize(2);
    }

    @Test
    void POKE_01_2_인증한_멤버는_찌를_수_없다() throws Exception {
        certify(userB);

        poke(userA, memberB, 1).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("POKE_003"));

        assertThat(pokeRepository.findAll()).isEmpty();
    }

    @Test
    void POKE_01_3_나를_찌를_수_없다() throws Exception {
        poke(userA, memberA, 1).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("POKE_001"));
    }

    @Test
    void POKE_01_4_다른_방_멤버는_찌를_수_없다() throws Exception {
        RoomMember memberE = addMember(newRoom(), newUser(), "은비");

        poke(userA, memberE, 1).andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("ROOM_009"));
    }

    @Test
    void POKE_01_5_투표_중에는_찌를_수_없다() throws Exception {
        // 어제(11/30) 회차가 아직 투표 중이라 오늘 회차 인증이 열리지 않았다.
        Round yesterday = Round.create(room, DEC_1.minusDays(1));
        yesterday.startVote(DEC_1.atTime(3, 0));
        roundRepository.save(yesterday);

        poke(userA, memberB, 1).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("POKE_002"));

        assertThat(pokeRepository.findAll()).isEmpty();
    }

    @Test
    void POKE_01_8_투표가_마감되면_다시_찌를_수_있다() throws Exception {
        Round yesterday = Round.create(room, DEC_1.minusDays(1));
        yesterday.startVote(DEC_1.atTime(3, 0));
        yesterday.announce(DEC_1.atTime(15, 0), RoundResultType.ALL_SAFE, null);
        roundRepository.save(yesterday);

        poke(userA, memberB, 1).andExpect(status().isCreated());

        assertThat(receivedCount(memberB)).isEqualTo(1);
    }

    @Test
    void POKE_01_6_오늘_회차_행이_없으면_만들고_쌓는다() throws Exception {
        assertThat(roundRepository.findAll()).isEmpty();

        poke(userA, memberB, 2).andExpect(status().isCreated());

        Round round = todayRound();
        assertThat(round.getRoundDate()).isEqualTo(DEC_1);
        assertThat(pokeRepository.findAll()).singleElement()
                .satisfies(p -> assertThat(p.getRound().getId()).isEqualTo(round.getId()));
    }

    @Test
    void POKE_01_7_한_번에_31번은_거부한다() throws Exception {
        poke(userA, memberB, 31).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("COMMON_002"));

        assertThat(pokeRepository.findAll()).isEmpty();
    }

    private ResultActions poke(User from, RoomMember to, int count) throws Exception {
        return mockMvc.perform(post("/api/rooms/{roomId}/pokes", room.getId())
                .header("Authorization", bearer(from))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"toMemberId\": " + to.getId() + ", \"count\": " + count + "}"));
    }

    private void certify(User user) throws Exception {
        mockMvc.perform(post("/api/rooms/{roomId}/certifications", room.getId())
                        .header("Authorization", bearer(user))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"result": "ENDURED", "memo": "집밥 해먹었어요"}
                                """))
                .andExpect(status().isCreated());
    }

    private int receivedCount(RoomMember member) {
        return pokeRepository.findAll().stream()
                .filter(p -> p.getToMember().getId().equals(member.getId()))
                .mapToInt(Poke::getCount)
                .sum();
    }

    private Round todayRound() {
        return roundRepository.findByRoom_IdAndRoundDate(room.getId(), DEC_1).orElseThrow();
    }

    private String bearer(User user) {
        return "Bearer " + jwtProvider.createAccessToken(user.getId());
    }

    private Room newRoom() {
        return roomRepository.save(Room.create("배달 끊기 방", RoomGoal.DELIVERY, 4, 300));
    }

    private User newUser() {
        return userRepository.save(User.create("anon-poke-" + userSequence++));
    }

    private RoomMember addMember(Room target, User user, String nickname) {
        return roomMemberRepository.save(RoomMember.create(target, user, nickname, BosalColor.PINK, DEC_1.minusDays(10).atStartOfDay()));
    }
}
