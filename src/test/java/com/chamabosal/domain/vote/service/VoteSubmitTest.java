package com.chamabosal.domain.vote.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.chamabosal.domain.certification.entity.Certification;
import com.chamabosal.domain.certification.entity.CertificationResult;
import com.chamabosal.domain.certification.repository.CertificationRepository;
import com.chamabosal.domain.rice.repository.RiceHistoryRepository;
import com.chamabosal.domain.room.entity.BosalColor;
import com.chamabosal.domain.room.entity.Room;
import com.chamabosal.domain.room.entity.RoomGoal;
import com.chamabosal.domain.room.entity.RoomMember;
import com.chamabosal.domain.room.repository.RoomMemberRepository;
import com.chamabosal.domain.room.repository.RoomRepository;
import com.chamabosal.domain.room.service.RoomService;
import com.chamabosal.domain.round.entity.Round;
import com.chamabosal.domain.round.entity.RoundStatus;
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
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/**
 * 공통 Given: 방 인원은 A, B, C, D 4명이고 모두 인증한 뒤 투표가 시작됐다 (VOTE-03).
 * 공양미·한 솥 보상(RICE-01)은 아직 없어서 검증하지 않는다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(MutableClock.Config.class)
class VoteSubmitTest {

    private static final LocalDate NOV_5 = LocalDate.of(2026, 11, 5);
    private static final LocalDateTime CERTIFIED_AT = NOV_5.atTime(20, 0);
    private static final LocalDateTime VOTE_STARTED_AT = NOV_5.plusDays(1).atTime(3, 0);

    @Autowired
    RiceHistoryRepository riceHistoryRepository;

    @Autowired
    MockMvc mockMvc;

    @Autowired
    MutableClock clock;

    @Autowired
    JwtProvider jwtProvider;

    @Autowired
    RoomService roomService;

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
    private Round round;

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
        round = startVotingRound(memberA, memberB, memberC, memberD);
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
    void VOTE_03_1_정상_제출() throws Exception {
        submit(userA, member(memberB)).andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.isClosed").value(false));
        assertThat(voteRepository.existsByRound_IdAndVoterMember_Id(round.getId(), memberA.getId())).isTrue();
    }

    @Test
    void VOTE_03_2_모두_보살_선택도_같은_보상() throws Exception {
        submit(userA, allBosal()).andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.isClosed").value(false));
        Vote vote = voteRepository.findByRound_Id(round.getId()).get(0);
        assertThat(vote.getTargetMember()).isNull();
    }

    @Test
    void VOTE_03_3_명단_밖_사용자_거부() throws Exception {
        User userE = newUser();
        RoomMember memberE = addMember(userE, "은우", BosalColor.PINK);

        submit(userE, member(memberA)).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("VOTE_002"));
    }

    @Test
    void VOTE_03_4_중복_제출() throws Exception {
        submit(userA, member(memberB)).andExpect(status().isCreated());

        submit(userA, member(memberC)).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("VOTE_004"));
        assertThat(voteRepository.findByRound_Id(round.getId())).hasSize(1);
    }

    @Test
    void VOTE_03_5_마감_후_제출() throws Exception {
        voteCloseService.closeAtDeadline(room.getId(), round.getId(), round.getVoteClosesAt());

        submit(userA, member(memberB)).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("VOTE_001"));
    }

    @Test
    void VOTE_03_6_투표_중_득표_비공개() throws Exception {
        submit(userA, member(memberB)).andExpect(status().isCreated());

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .get("/api/rooms/{roomId}/vote", room.getId())
                        .header("Authorization", "Bearer " + jwtProvider.createAccessToken(userB.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.votedCount").value(1))
                .andExpect(jsonPath("$.data.voterCount").value(4))
                .andExpect(jsonPath("$.data.candidates").isArray());
    }

    @Test
    void VOTE_03_7_마지막_두_표_동시_제출() throws Exception {
        submit(userA, member(memberB)).andExpect(status().isCreated());
        submit(userB, member(memberA)).andExpect(status().isCreated());

        CountDownLatch ready = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            CompletableFuture<Void> voteC = CompletableFuture.runAsync(() -> {
                await(ready);
                run(() -> submit(userC, member(memberA)));
            }, executor);
            CompletableFuture<Void> voteD = CompletableFuture.runAsync(() -> {
                await(ready);
                run(() -> submit(userD, member(memberA)));
            }, executor);
            ready.countDown();
            CompletableFuture.allOf(voteC, voteD).join();
        } finally {
            executor.shutdown();
        }

        assertThat(voteRepository.findByRound_Id(round.getId())).hasSize(4);
        assertThat(roundRepository.findById(round.getId()).orElseThrow().getStatus()).isEqualTo(RoundStatus.ANNOUNCED);
    }

    @Test
    void VOTE_03_8_다시_들어온_멤버는_투표_불가() {
        roomService.leave(userC.getId(), room.getId());
        clock.set(VOTE_STARTED_AT.plusHours(1));
        memberC.rejoin("하린", BosalColor.MINT, LocalDateTime.now(clock));
        roomMemberRepository.save(memberC);

        run(() -> submit(userC, member(memberA))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("VOTE_002")));
    }

    @Test
    void VOTE_03_9_미인증_멤버는_투표_불가() throws Exception {
        // 이 방은 한 번에 회차 하나만 투표 중일 수 있어, setUp의 회차와 겹치지 않는 새 방을 쓴다.
        Room otherRoom = roomRepository.save(Room.create("물욕 끊기 방", RoomGoal.SHOPPING, 4));
        User otherUserA = newUser();
        User otherUserB = newUser();
        User otherUserD = newUser();
        RoomMember otherMemberA = roomMemberRepository.save(
                RoomMember.create(otherRoom, otherUserA, "민지2", BosalColor.PINK, NOV_5.minusDays(10).atStartOfDay()));
        RoomMember otherMemberB = roomMemberRepository.save(
                RoomMember.create(otherRoom, otherUserB, "지수2", BosalColor.PURPLE, NOV_5.minusDays(10).atStartOfDay()));
        RoomMember otherMemberD = roomMemberRepository.save(
                RoomMember.create(otherRoom, otherUserD, "도윤2", BosalColor.YELLOW, NOV_5.minusDays(10).atStartOfDay()));
        Round otherRound = roundRepository.save(Round.create(otherRoom, NOV_5));
        certificationRepository.save(Certification.create(
                otherRound, otherMemberA, CertificationResult.ENDURED, "집밥 해먹었어요", null, CERTIFIED_AT));
        certificationRepository.save(Certification.create(
                otherRound, otherMemberB, CertificationResult.ENDURED, "집밥 해먹었어요", null, CERTIFIED_AT));
        // D는 인증하지 않는다. (3명 이상이어야 투표가 열린다)
        voteStartService.startAtDeadline(otherRoom.getId(), otherRound.getId(), VOTE_STARTED_AT);

        submit(otherUserD, otherRound.getId(), member(otherMemberA))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("VOTE_003"));
    }

    @Test
    void VOTE_03_본인_선택_거부() throws Exception {
        submit(userA, member(memberA)).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VOTE_005"));
    }

    @Test
    void VOTE_03_없는_회차_거부() throws Exception {
        submit(userA, round.getId() + 999L, member(memberB)).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ROUND_001"));
    }

    private org.springframework.test.web.servlet.ResultActions submit(User user, String body) throws Exception {
        return submit(user, round.getId(), body);
    }

    private org.springframework.test.web.servlet.ResultActions submit(User user, Long roundId, String body) throws Exception {
        return mockMvc.perform(post("/api/rounds/{roundId}/votes", roundId)
                .header("Authorization", "Bearer " + jwtProvider.createAccessToken(user.getId()))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    private String member(RoomMember target) {
        return """
                {"targetType": "MEMBER", "targetMemberId": %d}
                """.formatted(target.getId());
    }

    private String allBosal() {
        return """
                {"targetType": "ALL_BOSAL"}
                """;
    }

    private Round startVotingRound(RoomMember... certifiedMembers) {
        Round saved = roundRepository.save(Round.create(room, NOV_5));
        for (RoomMember member : certifiedMembers) {
            certificationRepository.save(Certification.create(
                    saved, member, CertificationResult.ENDURED, "집밥 해먹었어요", null, CERTIFIED_AT));
        }
        voteStartService.startAtDeadline(room.getId(), saved.getId(), VOTE_STARTED_AT);
        return roundRepository.findById(saved.getId()).orElseThrow();
    }

    private User newUser() {
        return userRepository.save(User.create("anon-submit-" + userSequence++));
    }

    private RoomMember addMember(User user, String nickname, BosalColor color) {
        return roomMemberRepository.save(RoomMember.create(room, user, nickname, color, NOV_5.minusDays(10).atStartOfDay()));
    }

    private static void await(CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private interface ThrowingRunnable {
        org.springframework.test.web.servlet.ResultActions run() throws Exception;
    }

    private void run(ThrowingRunnable runnable) {
        try {
            runnable.run();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
