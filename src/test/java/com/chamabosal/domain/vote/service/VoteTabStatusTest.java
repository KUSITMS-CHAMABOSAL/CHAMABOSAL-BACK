package com.chamabosal.domain.vote.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.chamabosal.domain.certification.entity.Certification;
import com.chamabosal.domain.certification.entity.CertificationResult;
import com.chamabosal.domain.certification.repository.CertificationRepository;
import com.chamabosal.domain.room.entity.BosalColor;
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
import com.chamabosal.domain.vote.entity.Vote;
import com.chamabosal.domain.vote.repository.VoteRepository;
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
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;

/**
 * 투표 탭 상태 (VOTE-05). 후보 목록(VOTE-02)은 `투표 중`·`투표권 없음` 상태의 candidates 필드로 함께 확인한다.
 * 공통 Given: 방 인원은 A, B, C, D 4명이다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(MutableClock.Config.class)
class VoteTabStatusTest {

    private static final LocalDate NOV_5 = LocalDate.of(2026, 11, 5);
    private static final LocalDateTime CERTIFIED_AT = NOV_5.atTime(20, 0);
    private static final LocalDateTime VOTE_STARTED_AT = NOV_5.plusDays(1).atTime(3, 0);

    @Autowired
    MockMvc mockMvc;

    @Autowired
    MutableClock clock;

    @Autowired
    JwtProvider jwtProvider;

    @Autowired
    VoteStartService voteStartService;

    @Autowired
    VoteCloseService voteCloseService;

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
    private User userD;
    private RoomMember memberA;
    private RoomMember memberB;
    private RoomMember memberC;
    private RoomMember memberD;

    @BeforeEach
    void setUp() {
        clock.set(CERTIFIED_AT);
        room = roomRepository.save(Room.create("배달 끊기 방", RoomGoal.DELIVERY, 4));
        userA = newUser();
        userB = newUser();
        userC = newUser();
        userD = newUser();
        memberA = addMember(userA, "민지", BosalColor.PINK);
        memberB = addMember(userB, "지수", BosalColor.PURPLE);
        memberC = addMember(userC, "하린", BosalColor.MINT);
        memberD = addMember(userD, "도윤", BosalColor.YELLOW);
    }

    @AfterEach
    void tearDown() {
        voteRepository.deleteAll();
        roundParticipantRepository.deleteAll();
        certificationRepository.deleteAll();
        roundRepository.deleteAll();
        roomMemberRepository.deleteAll();
        roomRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    void VOTE_05_1_3명_미만() throws Exception {
        Room smallRoom = roomRepository.save(Room.create("혼자 방", RoomGoal.DELIVERY, 4));
        User soloUser = newUser();
        roomMemberRepository.save(RoomMember.create(smallRoom, soloUser, "민지3", BosalColor.PINK, CERTIFIED_AT));

        tab(smallRoom.getId(), soloUser).andExpect(jsonPath("$.data.status").value("UNDER_THREE"))
                .andExpect(jsonPath("$.data.memberCount").value(1))
                .andExpect(jsonPath("$.data.memberLimit").value(4));
    }

    @Test
    void VOTE_05_2_첫_투표_전() throws Exception {
        tab(room.getId(), userA).andExpect(jsonPath("$.data.status").value("BEFORE_FIRST_VOTE"))
                .andExpect(jsonPath("$.data.uncertifiedCount").value(3));
    }

    @Test
    void VOTE_05_3_명단_밖() throws Exception {
        Round round = startVotingRound(memberA, memberB, memberC);
        User userE = newUser();
        roomMemberRepository.save(RoomMember.create(room, userE, "은우", BosalColor.PINK, VOTE_STARTED_AT.plusHours(1)));

        tab(room.getId(), userE).andExpect(jsonPath("$.data.status").value("NOT_IN_LIST"))
                .andExpect(jsonPath("$.data.roundId").value(round.getId()));
    }

    @Test
    void VOTE_05_4_투표_중() throws Exception {
        startVotingRound(memberA, memberB, memberC, memberD);

        tab(room.getId(), userA).andExpect(jsonPath("$.data.status").value("VOTING"))
                .andExpect(jsonPath("$.data.candidates.length()").value(3));
    }

    @Test
    void VOTE_05_5_투표_완료() throws Exception {
        Round round = startVotingRound(memberA, memberB, memberC, memberD);
        vote(round, memberA, memberB);

        tab(room.getId(), userA).andExpect(jsonPath("$.data.status").value("VOTED"))
                .andExpect(jsonPath("$.data.candidates").doesNotExist());
    }

    @Test
    void VOTE_05_6_결과_미확인() throws Exception {
        Round round = startVotingRound(memberA, memberB, memberC, memberD);
        vote(round, memberA, memberB);
        vote(round, memberB, memberA);
        vote(round, memberC, memberA);
        vote(round, memberD, memberA);
        voteCloseService.closeIfAllVoted(room.getId(), VOTE_STARTED_AT.plusHours(1));

        tab(room.getId(), userA).andExpect(jsonPath("$.data.status").value("RESULT_UNSEEN"))
                .andExpect(jsonPath("$.data.roundId").value(round.getId()));
    }

    @Test
    void VOTE_05_7_결과_확인_후() throws Exception {
        Round round = startVotingRound(memberA, memberB, memberC, memberD);
        vote(round, memberA, memberB);
        vote(round, memberB, memberA);
        vote(round, memberC, memberA);
        vote(round, memberD, memberA);
        voteCloseService.closeIfAllVoted(room.getId(), VOTE_STARTED_AT.plusHours(1));
        memberA.fillSeenResult(round.getRoundDate());
        roomMemberRepository.save(memberA);

        tab(room.getId(), userA).andExpect(jsonPath("$.data.status").value("RESULT_SEEN"));
    }

    @Test
    void VOTE_05_8_투표권_없음() throws Exception {
        startVotingRound(memberA, memberB, memberC);
        // D는 인증하지 않았다.

        tab(room.getId(), userD).andExpect(jsonPath("$.data.status").value("NO_VOTING_RIGHT"))
                .andExpect(jsonPath("$.data.candidates").isArray());
    }

    @Test
    void VOTE_05_9_투표_완료_화면_데이터() throws Exception {
        Round round = startVotingRound(memberA, memberB, memberC, memberD);
        vote(round, memberA, memberB);
        vote(round, memberB, memberA);

        tab(room.getId(), userA).andExpect(jsonPath("$.data.status").value("VOTED"))
                .andExpect(jsonPath("$.data.votedCount").value(2))
                .andExpect(jsonPath("$.data.voterCount").value(4))
                .andExpect(jsonPath("$.data.candidates").doesNotExist());
    }

    @Test
    void VOTE_05_10_투표권_없음_화면_데이터() throws Exception {
        startVotingRound(memberA, memberB, memberC);
        // D는 인증하지 않았다.

        tab(room.getId(), userD).andExpect(jsonPath("$.data.status").value("NO_VOTING_RIGHT"))
                // 투표권 있는 상태(VOTE_02_1)는 본인을 뺀 3명인데, 여기서는 나도 포함해 4명이다 (VOTE-05-10).
                .andExpect(jsonPath("$.data.candidates.length()").value(4));
    }

    @Test
    void VOTE_02_1_본인_제외() throws Exception {
        startVotingRound(memberA, memberB, memberC, memberD);

        tab(room.getId(), userA).andExpect(jsonPath("$.data.candidates.length()").value(3))
                .andExpect(jsonPath("$.data.candidates[?(@.memberId == " + memberA.getId() + ")]").isEmpty());
    }

    @Test
    void VOTE_02_3_휴식_중_표시() throws Exception {
        ReflectionTestUtils.setField(memberC, "restingRoundDate", NOV_5);
        roomMemberRepository.save(memberC);
        startVotingRound(memberA, memberB, memberC, memberD);

        tab(room.getId(), userA)
                .andExpect(jsonPath("$.data.candidates[?(@.memberId == " + memberC.getId() + ")].isResting")
                        .value(org.hamcrest.Matchers.contains(true)))
                .andExpect(jsonPath("$.data.candidates[?(@.memberId == " + memberC.getId() + ")].isSelectable")
                        .value(org.hamcrest.Matchers.contains(false)));
    }

    @Test
    void VOTE_02_5_기존_멤버_미인증은_후보() throws Exception {
        startVotingRound(memberA, memberC, memberD);
        // B는 인증하지 않았다.

        tab(room.getId(), userA)
                .andExpect(jsonPath("$.data.candidates[?(@.memberId == " + memberB.getId() + ")].isSelectable")
                        .value(org.hamcrest.Matchers.contains(true)));
    }

    @Test
    void VOTE_02_6_오늘_들어온_미인증_멤버_제외() throws Exception {
        RoomMember newcomer = roomMemberRepository.save(
                RoomMember.create(room, newUser(), "새온", BosalColor.PINK, CERTIFIED_AT));
        startVotingRound(memberA, memberB, memberC);
        // newcomer는 오늘 들어왔고 인증하지 않았다.

        tab(room.getId(), userA)
                .andExpect(jsonPath("$.data.candidates[?(@.memberId == " + newcomer.getId() + ")]").isEmpty());
    }

    @Test
    void VOTE_02_7_시작_후_참여자_제외() throws Exception {
        startVotingRound(memberA, memberB, memberC, memberD);
        User userE = newUser();
        RoomMember memberE = roomMemberRepository.save(
                RoomMember.create(room, userE, "은우", BosalColor.PINK, VOTE_STARTED_AT.plusHours(1)));

        tab(room.getId(), userA)
                .andExpect(jsonPath("$.data.candidates[?(@.memberId == " + memberE.getId() + ")]").isEmpty());
    }

    private org.springframework.test.web.servlet.ResultActions tab(Long roomId, User user) throws Exception {
        return mockMvc.perform(get("/api/rooms/{roomId}/vote", roomId)
                        .header("Authorization", "Bearer " + jwtProvider.createAccessToken(user.getId())))
                .andExpect(status().isOk());
    }

    private Round startVotingRound(RoomMember... certifiedMembers) {
        Round round = roundRepository.save(Round.create(room, NOV_5));
        for (RoomMember member : certifiedMembers) {
            certificationRepository.save(Certification.create(
                    round, member, CertificationResult.ENDURED, "집밥 해먹었어요", null, CERTIFIED_AT));
        }
        voteStartService.startAtDeadline(room.getId(), round.getId(), VOTE_STARTED_AT);
        return roundRepository.findById(round.getId()).orElseThrow();
    }

    private void vote(Round round, RoomMember voter, RoomMember target) {
        voteRepository.save(Vote.create(roundRepository.findById(round.getId()).orElseThrow(), voter, target));
    }

    private User newUser() {
        return userRepository.save(User.create("anon-tab-" + userSequence++));
    }

    private RoomMember addMember(User user, String nickname, BosalColor color) {
        return roomMemberRepository.save(RoomMember.create(room, user, nickname, color, NOV_5.minusDays(10).atStartOfDay()));
    }
}
