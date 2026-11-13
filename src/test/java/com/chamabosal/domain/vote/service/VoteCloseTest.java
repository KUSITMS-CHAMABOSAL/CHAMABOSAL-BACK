package com.chamabosal.domain.vote.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
import com.chamabosal.domain.room.service.RoomService;
import com.chamabosal.domain.round.entity.Round;
import com.chamabosal.domain.round.entity.RoundResultType;
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
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;

/**
 * 공통 Given: 방 인원은 A, B, C, D 4명이고 모두 인증했다. 투표 제출(VOTE-03)이 아직 없어서 Vote는 레포지토리로 직접 저장한다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(MutableClock.Config.class)
class VoteCloseTest {

    private static final LocalDate NOV_5 = LocalDate.of(2026, 11, 5);
    private static final LocalDateTime CERTIFIED_AT = NOV_5.atTime(20, 0);
    private static final LocalDateTime DEADLINE = NOV_5.plusDays(1).atTime(15, 0);

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
    VoteCloseScheduler voteCloseScheduler;

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
    void VOTE_04_1_전원_투표_즉시_마감() {
        Round round = startVotingRound(memberA, memberB, memberC, memberD);
        vote(round, memberA, null);
        vote(round, memberB, null);
        assertThat(voteCloseService.closeIfAllVoted(room.getId(), CERTIFIED_AT)).isFalse();

        vote(round, memberC, null);
        vote(round, memberD, memberA);

        assertThat(voteCloseService.closeIfAllVoted(room.getId(), CERTIFIED_AT)).isTrue();
        assertThat(reload(round).getStatus()).isEqualTo(RoundStatus.ANNOUNCED);
    }

    @Test
    void VOTE_04_2_15시_자동_마감() {
        Round round = startVotingRound(memberA, memberB, memberC, memberD);
        vote(round, memberA, null);
        vote(round, memberB, null);
        vote(round, memberC, null);
        // D는 투표하지 않는다.

        clock.set(DEADLINE);
        voteCloseScheduler.closeDueVotes();

        Round closed = reload(round);
        assertThat(closed.getStatus()).isEqualTo(RoundStatus.ANNOUNCED);
        assertThat(closed.getVoteClosedAt()).isEqualTo(DEADLINE);
        assertThat(closed.getResultType()).isEqualTo(RoundResultType.ALL_BOSAL);
    }

    @Test
    void VOTE_04_3_마감하면_다음_회차_인증이_열린다() throws Exception {
        Round round = startVotingRound(memberA, memberB, memberC, memberD);
        vote(round, memberA, null);
        vote(round, memberB, null);
        vote(round, memberC, null);
        vote(round, memberD, null);
        voteCloseService.closeIfAllVoted(room.getId(), CERTIFIED_AT);

        clock.set(NOV_5.plusDays(1).atTime(10, 0));
        mockMvc.perform(post("/api/rooms/{roomId}/certifications", room.getId())
                        .header("Authorization", "Bearer " + jwtProvider.createAccessToken(userA.getId()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"result": "ENDURED", "memo": "집밥 해먹었어요"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.roundDate").value(NOV_5.plusDays(1).toString()));
    }

    /**
     * 투표 제출(VOTE-03)이 없어서 "D의 투표"를 "D가 나가서 남은 명단 전원이 투표한 상태가 됨"으로 바꿔 두 실제 경로
     * (나가기 연결, 15:00 배치)가 같은 회차를 동시에 마감하려는 상황을 만든다. 둘 다 방 행 락을 먼저 잡아서 하나만 마감된다.
     */
    @Test
    void VOTE_04_4_나가서_전원_투표가_되는_것과_15시_자동_마감이_동시에() {
        Round round = startVotingRound(memberA, memberB, memberC, memberD);
        vote(round, memberA, null);
        vote(round, memberB, null);
        vote(round, memberC, null);
        clock.set(DEADLINE);

        CountDownLatch ready = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            CompletableFuture<Void> leave = CompletableFuture.runAsync(() -> {
                await(ready);
                roomService.leave(userD.getId(), room.getId());
            }, executor);
            CompletableFuture<Void> batch = CompletableFuture.runAsync(() -> {
                await(ready);
                voteCloseService.closeAtDeadline(room.getId(), round.getId(), DEADLINE);
            }, executor);
            ready.countDown();
            CompletableFuture.allOf(leave, batch).join();
        } finally {
            executor.shutdown();
        }

        Round closed = reload(round);
        assertThat(closed.getStatus()).isEqualTo(RoundStatus.ANNOUNCED);
        assertThat(voteRepository.findByRound_Id(round.getId())).hasSize(3);
    }

    @Test
    void VOTE_04_5_나간_멤버가_낸_표는_유지() {
        Round round = startVotingRound(memberA, memberB, memberC, memberD);
        vote(round, memberA, null);
        vote(round, memberB, null);
        vote(round, memberC, null);

        roomService.leave(userC.getId(), room.getId());
        assertThat(reload(round).getStatus()).isEqualTo(RoundStatus.VOTING);

        vote(round, memberD, null);
        assertThat(voteCloseService.closeIfAllVoted(room.getId(), CERTIFIED_AT)).isTrue();
        assertThat(reload(round).getStatus()).isEqualTo(RoundStatus.ANNOUNCED);
        assertThat(voteRepository.findByRound_Id(round.getId())).hasSize(4);
    }

    @Test
    void VOTE_04_6_투표하지_않고_나간_멤버는_기권() {
        Round round = startVotingRound(memberA, memberB, memberC, memberD);
        vote(round, memberA, null);
        vote(round, memberB, null);

        roomService.leave(userC.getId(), room.getId());
        assertThat(reload(round).getStatus()).isEqualTo(RoundStatus.VOTING);

        vote(round, memberD, null);
        assertThat(voteCloseService.closeIfAllVoted(room.getId(), CERTIFIED_AT)).isTrue();
        assertThat(voteRepository.findByRound_Id(round.getId())).hasSize(3);
    }

    @Test
    void VOTE_04_7_미인증_멤버는_기다리지_않음() {
        Round round = startVotingRound(memberA, memberB, memberC);
        vote(round, memberA, null);
        vote(round, memberB, null);

        assertThat(voteCloseService.closeIfAllVoted(room.getId(), CERTIFIED_AT)).isFalse();
        vote(round, memberC, null);

        assertThat(voteCloseService.closeIfAllVoted(room.getId(), CERTIFIED_AT)).isTrue();
    }

    @Test
    void RESULT_01_1_과반_모두_보살() {
        Round round = votingRound();
        vote(round, memberA, null);
        vote(round, memberB, null);
        vote(round, memberC, null);
        vote(round, memberD, memberA);

        voteCloseService.closeAtDeadline(room.getId(), round.getId(), DEADLINE);

        Round closed = reload(round);
        assertThat(closed.getResultType()).isEqualTo(RoundResultType.ALL_BOSAL);
        assertThat(closed.getPickedMember()).isNull();
    }

    @Test
    void RESULT_01_2_실제_투표자_기준_과반() {
        Round round = votingRound();
        vote(round, memberA, null);
        vote(round, memberB, null);
        vote(round, memberC, memberD);

        voteCloseService.closeAtDeadline(room.getId(), round.getId(), DEADLINE);

        assertThat(reload(round).getResultType()).isEqualTo(RoundResultType.ALL_BOSAL);
    }

    @Test
    void RESULT_01_3_정확히_절반은_과반_아님() {
        Round round = votingRound();
        vote(round, memberA, null);
        vote(round, memberB, null);
        vote(round, memberC, memberA);
        vote(round, memberD, memberA);

        voteCloseService.closeAtDeadline(room.getId(), round.getId(), DEADLINE);

        Round closed = reload(round);
        assertThat(closed.getResultType()).isEqualTo(RoundResultType.PICKED);
        assertThat(closed.getPickedMember().getId()).isEqualTo(memberA.getId());
    }

    @Test
    void RESULT_01_4_단독_최다() {
        Round round = votingRound();
        vote(round, memberA, memberB);
        vote(round, memberB, null);
        vote(round, memberC, memberA);
        vote(round, memberD, memberA);

        voteCloseService.closeAtDeadline(room.getId(), round.getId(), DEADLINE);

        Round closed = reload(round);
        assertThat(closed.getResultType()).isEqualTo(RoundResultType.PICKED);
        assertThat(closed.getPickedMember().getId()).isEqualTo(memberA.getId());
    }

    @Test
    void RESULT_01_5_득표_동점이면_모두_무사() {
        Round round = votingRound();
        vote(round, memberA, memberB);
        vote(round, memberB, memberA);
        vote(round, memberC, memberA);
        vote(round, memberD, memberB);

        voteCloseService.closeAtDeadline(room.getId(), round.getId(), DEADLINE);

        Round closed = reload(round);
        assertThat(closed.getResultType()).isEqualTo(RoundResultType.ALL_SAFE);
        assertThat(closed.getPickedMember()).isNull();
    }

    @Test
    void RESULT_01_6_세_명_동점() {
        Round round = votingRound();
        vote(round, memberA, memberB);
        vote(round, memberB, memberC);
        vote(round, memberC, memberA);
        vote(round, memberD, null);

        voteCloseService.closeAtDeadline(room.getId(), round.getId(), DEADLINE);

        assertThat(reload(round).getResultType()).isEqualTo(RoundResultType.ALL_SAFE);
    }

    @Test
    void RESULT_01_7_0표() {
        Round round = votingRound();

        voteCloseService.closeAtDeadline(room.getId(), round.getId(), DEADLINE);

        Round closed = reload(round);
        assertThat(closed.getResultType()).isEqualTo(RoundResultType.ALL_SAFE);
        assertThat(closed.getPickedMember()).isNull();
    }

    /** ROOM-07-6: 투표 중 나가도 표는 결과에 반영된다 (VOTE-04-5와 같은 처리를 다른 관점에서 확인). */
    @Test
    void ROOM_07_6_투표_중_나가도_표는_유지된다() {
        Round round = startVotingRound(memberA, memberB, memberC, memberD);
        vote(round, memberA, memberB);

        roomService.leave(userA.getId(), room.getId());

        assertThat(voteRepository.findByRound_Id(round.getId()))
                .extracting(vote -> vote.getVoterMember().getId())
                .containsExactly(memberA.getId());
    }

    /** ROOM-07-7: 투표하지 않은 멤버가 나가서 남은 명단 전원이 투표한 상태가 되면 바로 마감된다. */
    @Test
    void ROOM_07_7_나가서_전원_투표가_됨() {
        Round round = startVotingRound(memberA, memberB, memberC);
        vote(round, memberA, null);
        vote(round, memberB, null);

        roomService.leave(userC.getId(), room.getId());

        assertThat(reload(round).getStatus()).isEqualTo(RoundStatus.ANNOUNCED);
    }

    /** 명단 멤버 전원을 인증한 상태로 03:00 배치에 넘겨 VOTING으로 만든다. */
    private Round startVotingRound(RoomMember... certifiedMembers) {
        Round round = roundRepository.save(Round.create(room, NOV_5));
        for (RoomMember member : certifiedMembers) {
            certificationRepository.save(Certification.create(
                    round, member, CertificationResult.ENDURED, "집밥 해먹었어요", null, CERTIFIED_AT));
        }
        voteStartService.startAtDeadline(room.getId(), round.getId(), NOV_5.plusDays(1).atTime(3, 0));
        return reload(round);
    }

    /** RESULT-01 판정만 확인할 때는 명단 스냅샷 없이 VOTING 상태만 만들어 둔다. */
    private Round votingRound() {
        Round round = Round.create(room, NOV_5);
        ReflectionTestUtils.setField(round, "status", RoundStatus.VOTING);
        ReflectionTestUtils.setField(round, "voteStartedAt", NOV_5.plusDays(1).atTime(3, 0));
        return roundRepository.save(round);
    }

    private void vote(Round round, RoomMember voter, RoomMember target) {
        voteRepository.save(Vote.create(reload(round), voter, target));
    }

    private Round reload(Round round) {
        return roundRepository.findById(round.getId()).orElseThrow();
    }

    private User newUser() {
        return userRepository.save(User.create("anon-close-" + userSequence++));
    }

    private RoomMember addMember(User user, String nickname, BosalColor color) {
        return roomMemberRepository.save(RoomMember.create(room, user, nickname, color, NOV_5.minusDays(10).atStartOfDay()));
    }

    private static void await(CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }
}
