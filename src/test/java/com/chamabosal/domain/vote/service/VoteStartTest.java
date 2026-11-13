package com.chamabosal.domain.vote.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.chamabosal.domain.certification.repository.CertificationRepository;
import com.chamabosal.domain.room.entity.BosalColor;
import com.chamabosal.domain.room.entity.Room;
import com.chamabosal.domain.room.entity.RoomGoal;
import com.chamabosal.domain.room.entity.RoomMember;
import com.chamabosal.domain.room.repository.RoomMemberRepository;
import com.chamabosal.domain.room.repository.RoomRepository;
import com.chamabosal.domain.round.entity.Round;
import com.chamabosal.domain.round.entity.RoundParticipant;
import com.chamabosal.domain.round.entity.RoundStatus;
import com.chamabosal.domain.round.repository.RoundParticipantRepository;
import com.chamabosal.domain.round.repository.RoundRepository;
import com.chamabosal.domain.user.entity.User;
import com.chamabosal.domain.user.repository.UserRepository;
import com.chamabosal.global.security.jwt.JwtProvider;
import com.chamabosal.support.MutableClock;
import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/** 11/5 회차를 기준으로, 인증은 11/5 20:00에 하고 03:00 배치는 11/6 03:00에 돌린다. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(MutableClock.Config.class)
class VoteStartTest {

    private static final LocalDate NOV_5 = LocalDate.of(2026, 11, 5);
    private static final LocalDateTime CERTIFIED_AT = NOV_5.atTime(20, 0);
    private static final LocalDateTime DEADLINE = NOV_5.plusDays(1).atTime(3, 0);

    @Autowired
    MockMvc mockMvc;

    @Autowired
    MutableClock clock;

    @Autowired
    JwtProvider jwtProvider;

    @Autowired
    VoteStartScheduler voteStartScheduler;

    @Autowired
    VoteStartService voteStartService;

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

    private int userSequence;
    private Room room;

    @BeforeEach
    void setUp() {
        clock.set(CERTIFIED_AT);
        room = roomRepository.save(Room.create("배달 끊기 방", RoomGoal.DELIVERY, 4));
    }

    @AfterEach
    void tearDown() {
        roundParticipantRepository.deleteAll();
        certificationRepository.deleteAll();
        roundRepository.deleteAll();
        roomMemberRepository.deleteAll();
        roomRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    void VOTE_01_1_전원_인증하면_바로_시작하고_명단에는_오늘_들어온_멤버와_휴식_중인_멤버도_있다() throws Exception {
        RoomMember a = addMember("민지");
        RoomMember b = addMember("지수");
        RoomMember c = addMember("하린");
        RoomMember d = addMemberJoinedAt("도윤", NOV_5.atTime(10, 0));
        rest(c);
        certify(a);
        certify(b);
        certify(c);

        certify(d).andExpect(jsonPath("$.data.isVoteStarted").value(true));

        Round round = nov5();
        assertThat(round.getStatus()).isEqualTo(RoundStatus.VOTING);
        assertThat(participants(round)).containsOnlyKeys(a.getId(), b.getId(), c.getId(), d.getId());
    }

    @Test
    void VOTE_01_2_전원_인증이_아니면_다음_날_03시에_시작하고_15시까지_열린다() throws Exception {
        RoomMember a = addMember("민지");
        RoomMember b = addMember("지수");
        addMember("하린");
        addMember("도윤");
        certify(a);
        certify(b);
        assertThat(nov5().getStatus()).isEqualTo(RoundStatus.CERTIFYING);

        runDeadlineBatch();

        Round round = nov5();
        assertThat(round.getStatus()).isEqualTo(RoundStatus.VOTING);
        assertThat(round.getVoteStartedAt()).isEqualTo(DEADLINE);
        assertThat(round.getVoteClosesAt()).isEqualTo(NOV_5.plusDays(1).atTime(15, 0));
        assertThat(participants(round)).hasSize(4);
    }

    @Test
    void VOTE_01_3_명단이_3명_미만이면_투표_없음이고_다음_회차_인증이_바로_열린다() throws Exception {
        RoomMember a = addMember("민지");
        RoomMember b = addMember("지수");
        certify(a);

        runDeadlineBatch();

        Round round = nov5();
        assertThat(round.getStatus()).isEqualTo(RoundStatus.NO_VOTE);
        assertThat(round.getVoteStartedAt()).isEqualTo(DEADLINE);
        assertThat(participants(round)).isEmpty();
        clock.set(DEADLINE.plusMinutes(10));
        certify(b)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.roundDate").value("2026-11-06"));
    }

    @Test
    void VOTE_01_5_마지막_인증과_03시_배치가_동시에_실행돼도_투표는_하나다() {
        List<RoomMember> members = List.of(addMember("민지"), addMember("지수"), addMember("하린"), addMember("도윤"));
        members.subList(0, 3).forEach(this::certifyQuietly);
        Round round = nov5();
        clock.set(DEADLINE.minusSeconds(1));

        CountDownLatch ready = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        MockHttpServletResponse response;
        try {
            CompletableFuture<MockHttpServletResponse> lastCertification = CompletableFuture.supplyAsync(() -> {
                await(ready);
                try {
                    return requestCertification(members.get(3)).andReturn().getResponse();
                } catch (Exception e) {
                    throw new IllegalStateException(e);
                }
            }, executor);
            CompletableFuture<Void> batch = CompletableFuture.runAsync(() -> {
                await(ready);
                voteStartService.startAtDeadline(room.getId(), round.getId(), DEADLINE);
            }, executor);
            ready.countDown();
            CompletableFuture.allOf(lastCertification, batch).join();
            response = lastCertification.join();
        } finally {
            executor.shutdown();
        }

        // 인증이 먼저면 201로 투표를 열고, 배치가 먼저면 투표가 시작된 회차라 CERT_006이다. 그 밖의 응답은 실패다.
        String body = contentOf(response);
        if (response.getStatus() == 201) {
            assertThat(body).contains("\"isVoteStarted\":true");
        } else {
            assertThat(response.getStatus()).isEqualTo(409);
            assertThat(body).contains("\"code\":\"CERT_006\"");
        }
        assertThat(nov5().getStatus()).isEqualTo(RoundStatus.VOTING);
        assertThat(participants(nov5())).hasSize(4);
    }

    /** 시나리오 없음: 서버가 03:00에 꺼져 있어 배치가 늦게 돌 때, 그 사이 들어온 멤버는 인증할 수 없었으므로 후보가 아니다 (코드리뷰 반영). */
    @Test
    void 배치가_늦게_돌면_회차_날짜_뒤에_들어온_미인증_멤버는_후보가_아니다() throws Exception {
        RoomMember a = addMember("민지");
        RoomMember b = addMember("지수");
        addMember("하린");
        certify(a);
        certify(b);
        RoomMember late = addMemberJoinedAt("도윤", DEADLINE.plusHours(1));

        clock.set(DEADLINE.plusHours(2));
        voteStartScheduler.startDueVotes();

        RoundParticipant participant = participants(nov5()).get(late.getId());
        assertThat(participant.isCandidate()).isFalse();
        assertThat(participant.isCanVote()).isFalse();
    }

    private static String contentOf(MockHttpServletResponse response) {
        try {
            return response.getContentAsString(StandardCharsets.UTF_8);
        } catch (UnsupportedEncodingException e) {
            throw new IllegalStateException(e);
        }
    }

    @Test
    void VOTE_01_7_미인증_멤버는_명단에_있지만_투표권이_없다() throws Exception {
        RoomMember a = addMember("민지");
        RoomMember b = addMember("지수");
        RoomMember c = addMember("하린");
        RoomMember d = addMember("도윤");
        certify(a);
        certify(b);

        runDeadlineBatch();

        Map<Long, RoundParticipant> participants = participants(nov5());
        assertThat(participants).hasSize(4);
        assertThat(participants.get(a.getId()).isCanVote()).isTrue();
        assertThat(participants.get(b.getId()).isCanVote()).isTrue();
        assertThat(participants.get(c.getId()).isCanVote()).isFalse();
        assertThat(participants.get(d.getId()).isCanVote()).isFalse();
    }

    @Test
    void VOTE_01_8_인증한_멤버가_1명이어도_투표를_연다() throws Exception {
        RoomMember a = addMember("민지");
        addMember("지수");
        addMember("하린");
        certify(a);

        runDeadlineBatch();

        Round round = nov5();
        assertThat(round.getStatus()).isEqualTo(RoundStatus.VOTING);
        assertThat(participants(round).values()).filteredOn(RoundParticipant::isCanVote)
                .extracting(participant -> participant.getMember().getId())
                .containsExactly(a.getId());
    }

    @Test
    void VOTE_01_9_인증한_멤버가_0명이면_투표_없음이다() {
        addMember("민지");
        addMember("지수");
        addMember("하린");
        roundRepository.save(Round.create(room, NOV_5));

        runDeadlineBatch();

        assertThat(nov5().getStatus()).isEqualTo(RoundStatus.NO_VOTE);
        assertThat(participants(nov5())).isEmpty();
    }

    @Test
    void CERT_05_1_인증하지_않은_기존_멤버는_선택할_수_있는_후보다() throws Exception {
        RoomMember b = startWithUncertified(addMember("지수"));

        RoundParticipant participant = participants(nov5()).get(b.getId());
        assertThat(participant.isCandidate()).isTrue();
        assertThat(participant.isResting()).isFalse();
    }

    @Test
    void CERT_05_2_오늘_들어와서_인증하지_않은_멤버는_후보도_투표권도_없다() throws Exception {
        RoomMember d = startWithUncertified(addMemberJoinedAt("도윤", NOV_5.atTime(10, 0)));

        RoundParticipant participant = participants(nov5()).get(d.getId());
        assertThat(participant.isCandidate()).isFalse();
        assertThat(participant.isCanVote()).isFalse();
    }

    @Test
    void CERT_05_3_휴식_중인_미인증_멤버는_명단에_있지만_선택할_수_없다() throws Exception {
        RoomMember c = addMember("하린");
        rest(c);
        startWithUncertified(c);

        RoundParticipant participant = participants(nov5()).get(c.getId());
        assertThat(participant.isResting()).isTrue();
        assertThat(participant.isCandidate()).isFalse();
        assertThat(participant.isCanVote()).isFalse();
    }

    @Test
    void CERT_05_4_인증하지_않은_기존_멤버는_후보지만_투표할_수_없다() throws Exception {
        RoomMember b = startWithUncertified(addMember("지수"));

        RoundParticipant participant = participants(nov5()).get(b.getId());
        assertThat(participant.isCandidate()).isTrue();
        assertThat(participant.isCanVote()).isFalse();
    }

    /** 인증한 멤버 2명과 인증하지 않은 uncertified로 03:00 투표를 시작한다. */
    private RoomMember startWithUncertified(RoomMember uncertified) throws Exception {
        certify(addMember("민지"));
        certify(addMember("서연"));
        runDeadlineBatch();
        assertThat(nov5().getStatus()).isEqualTo(RoundStatus.VOTING);
        return uncertified;
    }

    private void runDeadlineBatch() {
        clock.set(DEADLINE);
        voteStartScheduler.startDueVotes();
    }

    private ResultActions certify(RoomMember member) throws Exception {
        return requestCertification(member).andExpect(status().isCreated());
    }

    private ResultActions requestCertification(RoomMember member) throws Exception {
        return mockMvc.perform(post("/api/rooms/{roomId}/certifications", room.getId())
                .header("Authorization", "Bearer " + jwtProvider.createAccessToken(member.getUser().getId()))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"result": "ENDURED", "memo": "집밥 해먹었어요"}
                        """));
    }

    private void certifyQuietly(RoomMember member) {
        try {
            certify(member);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private Round nov5() {
        return roundRepository.findByRoom_IdAndRoundDate(room.getId(), NOV_5).orElseThrow();
    }

    private Map<Long, RoundParticipant> participants(Round round) {
        return roundParticipantRepository.findByRound_Id(round.getId()).stream()
                .collect(Collectors.toMap(participant -> participant.getMember().getId(), Function.identity()));
    }

    private void rest(RoomMember member) {
        ReflectionTestUtils.setField(member, "restingRoundDate", NOV_5);
        roomMemberRepository.save(member);
    }

    private RoomMember addMember(String nickname) {
        return addMemberJoinedAt(nickname, NOV_5.minusDays(10).atStartOfDay());
    }

    private RoomMember addMemberJoinedAt(String nickname, LocalDateTime joinedAt) {
        User user = userRepository.save(User.create("anon-vote-" + userSequence++));
        BosalColor color = BosalColor.values()[(int) roomMemberRepository.countByRoom_IdAndLeftAtIsNull(room.getId())];
        return roomMemberRepository.save(RoomMember.create(room, user, nickname, color, joinedAt));
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
