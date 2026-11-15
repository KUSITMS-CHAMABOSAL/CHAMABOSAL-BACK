package com.chamabosal.domain.defense.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.chamabosal.domain.certification.entity.Certification;
import com.chamabosal.domain.certification.entity.CertificationResult;
import com.chamabosal.domain.certification.repository.CertificationRepository;
import com.chamabosal.domain.defense.entity.DefenseTicket;
import com.chamabosal.domain.defense.repository.DefenseTicketRepository;
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
import com.chamabosal.domain.round.entity.Round;
import com.chamabosal.domain.round.entity.RoundResultType;
import com.chamabosal.domain.round.entity.RoundStatus;
import com.chamabosal.domain.round.repository.RoundParticipantRepository;
import com.chamabosal.domain.round.repository.RoundRepository;
import com.chamabosal.domain.tap.repository.TapRepository;
import com.chamabosal.domain.user.entity.User;
import com.chamabosal.domain.user.repository.UserRepository;
import com.chamabosal.domain.vote.entity.Vote;
import com.chamabosal.domain.vote.repository.VoteRepository;
import com.chamabosal.domain.vote.service.VoteCloseService;
import com.chamabosal.domain.vote.service.VoteStartService;
import com.chamabosal.global.security.jwt.JwtProvider;
import com.chamabosal.support.MutableClock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Supplier;
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

/**
 * 방어권 구매(SHIELD-01)·사용(SHIELD-02), 목탁 수행 완료(RICE-01 10·11·13·14번).
 * 방마다 A, B, C, D 4명이고, 11/4 회차를 실제 흐름(인증 → 03:00 투표 시작 → 표 → 15:00 마감)으로 발표해 A를 지목한다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(MutableClock.Config.class)
class DefenseMoktakTest {

    private static final LocalDate NOV_4 = LocalDate.of(2026, 11, 4);
    private static final LocalDate NOV_5 = LocalDate.of(2026, 11, 5);
    private static final LocalDateTime AFTER_CLOSE = NOV_5.atTime(16, 0);

    @Autowired MockMvc mockMvc;
    @Autowired MutableClock clock;
    @Autowired JwtProvider jwtProvider;
    @Autowired VoteStartService voteStartService;
    @Autowired VoteCloseService voteCloseService;
    @Autowired UserRepository userRepository;
    @Autowired RoomRepository roomRepository;
    @Autowired RoomMemberRepository roomMemberRepository;
    @Autowired RoundRepository roundRepository;
    @Autowired RoundParticipantRepository roundParticipantRepository;
    @Autowired CertificationRepository certificationRepository;
    @Autowired VoteRepository voteRepository;
    @Autowired TapRepository tapRepository;
    @Autowired RiceHistoryRepository riceHistoryRepository;
    @Autowired DefenseTicketRepository defenseTicketRepository;

    private int userSequence;
    private long refSequence = 1;
    private User userA;
    private User userB;
    private Party party;

    /** 한 방의 멤버 4명. a는 언제나 userA다. */
    private record Party(Room room, RoomMember a, RoomMember b, RoomMember c, RoomMember d) {
    }

    @BeforeEach
    void setUp() {
        clock.set(AFTER_CLOSE);
        userA = newUser();
        userB = newUser();
        party = newParty(userB);
    }

    @AfterEach
    void tearDown() {
        defenseTicketRepository.deleteAll();
        riceHistoryRepository.deleteAll();
        tapRepository.deleteAll();
        voteRepository.deleteAll();
        roundParticipantRepository.deleteAll();
        certificationRepository.deleteAll();
        roundRepository.deleteAll();
        roomMemberRepository.deleteAll();
        roomRepository.deleteAll();
        userRepository.deleteAll();
    }

    // ---------- SHIELD-01 방어권 구매 ----------

    @Test
    void SHIELD_01_1_구매() throws Exception {
        earn(userA, 120);

        buy(userA).andExpect(status().isCreated()).andExpect(jsonPath("$.data.riceBalance").value(20));

        assertThat(ticketCount(userA)).isEqualTo(1);
        assertThat(balance(userA)).isEqualTo(20);
    }

    @Test
    void SHIELD_01_2_보유_중에도_구매() throws Exception {
        giveTickets(userA, 1);
        earn(userA, 100);

        buy(userA).andExpect(status().isCreated()).andExpect(jsonPath("$.data.riceBalance").value(0));

        assertThat(ticketCount(userA)).isEqualTo(2);
    }

    @Test
    void SHIELD_01_3_잔액_부족() throws Exception {
        earn(userA, 90);

        buy(userA).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("STORE_001"));

        assertThat(ticketCount(userA)).isZero();
        assertThat(balance(userA)).isEqualTo(90);
    }

    @Test
    void SHIELD_01_4_동시_구매() {
        earn(userA, 150);

        List<Integer> statuses = concurrently(() -> statusOf(() -> buy(userA)), () -> statusOf(() -> buy(userA)));

        assertThat(statuses).containsExactlyInAnyOrder(201, 409);
        assertThat(ticketCount(userA)).isEqualTo(1);
        assertThat(balance(userA)).isEqualTo(50);
    }

    // ---------- SHIELD-02 방어권 사용 ----------

    @Test
    void SHIELD_02_1_사용() throws Exception {
        giveTickets(userA, 1);
        Round round = pickedA(party);
        result(userA, round);

        clock.set(AFTER_CLOSE.plusMinutes(1));
        defend(userA, round).andExpect(status().isCreated());

        assertThat(reload(round).getResultType()).isEqualTo(RoundResultType.DEFENDED);
        assertThat(ticketCount(userA)).isZero();
    }

    @Test
    void SHIELD_02_2_방어_시간_지남() throws Exception {
        giveTickets(userA, 1);
        Round round = pickedA(party);
        result(userA, round);

        clock.set(AFTER_CLOSE.plusMinutes(2).plusSeconds(1));
        defend(userA, round).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("DEFENSE_009"));

        assertThat(reload(round).getResultType()).isEqualTo(RoundResultType.PICKED);
        assertThat(ticketCount(userA)).isEqualTo(1);
    }

    @Test
    void SHIELD_02_3_지목된_사람이_아님() throws Exception {
        giveTickets(userB, 1);
        Round round = pickedA(party);

        defend(userB, round).andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("DEFENSE_008"));
    }

    @Test
    void SHIELD_02_4_여러_번_사용() throws Exception {
        Party other = newParty(newUser());
        Round otherRound = pickedA(other);
        Round round = pickedA(party);
        giveTickets(userA, 1);
        result(userA, otherRound);
        defend(userA, otherRound).andExpect(status().isCreated());
        earn(userA, 100);
        buy(userA).andExpect(status().isCreated());

        result(userA, round);
        clock.set(AFTER_CLOSE.plusMinutes(1));
        defend(userA, round).andExpect(status().isCreated());

        assertThat(reload(round).getResultType()).isEqualTo(RoundResultType.DEFENDED);
    }

    @Test
    void SHIELD_02_5_방어권_없음() throws Exception {
        Round round = pickedA(party);
        result(userA, round);

        clock.set(AFTER_CLOSE.plusMinutes(1));
        defend(userA, round).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("DEFENSE_002"));
    }

    @Test
    void SHIELD_02_6_결과를_보지_않고_바로_사용() throws Exception {
        giveTickets(userA, 1);
        Round round = pickedA(party);

        defend(userA, round).andExpect(status().isCreated());

        assertThat(reload(round).getPickedSeenAt()).isEqualTo(AFTER_CLOSE);
    }

    @Test
    void SHIELD_02_7_방어하면_톡톡_무효() throws Exception {
        giveTickets(userA, 1);
        Round round = pickedA(party);
        tap(userB, round, 5).andExpect(status().isCreated());

        defend(userA, round).andExpect(status().isCreated());

        assertThat(reload(round).getResultType()).isEqualTo(RoundResultType.DEFENDED);
        mockMvc.perform(get("/api/rounds/{roundId}/taps/received", round.getId()).header("Authorization", bearer(userA)))
                .andExpect(jsonPath("$.data.taps").isEmpty());
    }

    @Test
    void SHIELD_02_8_같은_요청_반복() throws Exception {
        giveTickets(userA, 2);
        Round round = pickedA(party);
        defend(userA, round).andExpect(status().isCreated());

        defend(userA, round).andExpect(status().isCreated());

        assertThat(ticketCount(userA)).isEqualTo(1);
    }

    /** 두 방은 서로 다른 방 행 락을 쓴다. 사용자 행 락이 없으면 둘 다 같은 방어권을 읽어 두 방 모두 방어된다. */
    @Test
    void SHIELD_02_9_방어권_하나로_두_방에서_동시_사용() throws Exception {
        giveTickets(userA, 1);
        Party other = newParty(newUser());
        Round round = pickedA(party);
        Round otherRound = pickedA(other);
        result(userA, round);
        result(userA, otherRound);

        List<Integer> statuses = concurrently(
                () -> statusOf(() -> defend(userA, round)), () -> statusOf(() -> defend(userA, otherRound)));

        assertThat(statuses).containsExactlyInAnyOrder(201, 409);
        assertThat(List.of(reload(round).getResultType(), reload(otherRound).getResultType()))
                .containsExactlyInAnyOrder(RoundResultType.DEFENDED, RoundResultType.PICKED);
        assertThat(ticketCount(userA)).isZero();
    }

    @Test
    void SHIELD_02_10_사유가_여럿() throws Exception {
        Round round = pickedA(party);

        defend(userB, round).andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("DEFENSE_008"));
    }

    @Test
    void SHIELD_02_11_방어권_두_개로_두_방에서_사용() throws Exception {
        giveTickets(userA, 2);
        Party other = newParty(newUser());
        Round round = pickedA(party);
        Round otherRound = pickedA(other);

        defend(userA, round).andExpect(status().isCreated());
        defend(userA, otherRound).andExpect(status().isCreated());

        assertThat(reload(round).getResultType()).isEqualTo(RoundResultType.DEFENDED);
        assertThat(reload(otherRound).getResultType()).isEqualTo(RoundResultType.DEFENDED);
        assertThat(ticketCount(userA)).isZero();
    }

    @Test
    void SHIELD_02_12_목탁_수행을_마친_뒤_방어() throws Exception {
        giveTickets(userA, 1);
        Round round = pickedA(party);
        tap(userB, round, 5).andExpect(status().isCreated());
        moktak(userA, round, 5).andExpect(status().isCreated());

        clock.set(AFTER_CLOSE.plusMinutes(1));
        defend(userA, round).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("DEFENSE_010"));

        assertThat(reload(round).getResultType()).isEqualTo(RoundResultType.PICKED);
        assertThat(beonnoe(userA)).hasSize(1);
        assertThat(ticketCount(userA)).isEqualTo(1);
    }

    /** 결과 화면의 사용 가능 여부도 SHIELD-02 12번을 따른다 (코드리뷰 반영). */
    @Test
    void 목탁_수행을_마치면_결과_화면에서_방어권을_쓸_수_없다() throws Exception {
        giveTickets(userA, 1);
        Round round = pickedA(party);
        tap(userB, round, 5).andExpect(status().isCreated());
        result(userA, round).andExpect(jsonPath("$.data.canUseDefense").value(true));

        moktak(userA, round, 5).andExpect(status().isCreated());

        result(userA, round)
                .andExpect(jsonPath("$.data.resultType").value("PICKED"))
                .andExpect(jsonPath("$.data.defenseTicketCount").value(1))
                .andExpect(jsonPath("$.data.canUseDefense").value(false));
    }

    /** 3번: 결과를 열지 않고 방어권 없이 보내 거부돼도 처음 조회 시각은 남아, 나중에 사서 쓰면 그 시각부터 2분이다 (코드리뷰 반영). */
    @Test
    void 방어권이_없어_거부돼도_처음_조회_시각은_남는다() throws Exception {
        Round round = pickedA(party);
        defend(userA, round).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("DEFENSE_002"));
        assertThat(reload(round).getPickedSeenAt()).isEqualTo(AFTER_CLOSE);

        giveTickets(userA, 1);
        clock.set(AFTER_CLOSE.plusMinutes(5));
        defend(userA, round).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("DEFENSE_009"));
    }

    /** 8번 (Q-76 임시 결정): 인증하지 않아 `인증 안 함`으로 지목된 사람도 쓸 수 있다. */
    @Test
    void 인증_안_함으로_지목된_사람도_방어권을_쓸_수_있다() throws Exception {
        giveTickets(userA, 1);
        Round round = announced(party, NOV_4, false);

        defend(userA, round).andExpect(status().isCreated());

        assertThat(reload(round).getResultType()).isEqualTo(RoundResultType.DEFENDED);
    }

    /** 3번 (Q-77 임시 결정): 결과를 열지 않은 채 다음 투표가 열리면 처음 열어도 쓸 수 없다. */
    @Test
    void 결과를_열지_않고_다음_투표가_열리면_방어권을_쓸_수_없다() throws Exception {
        giveTickets(userA, 1);
        Round round = pickedA(party);
        startVoting(party, NOV_5, true);

        result(userA, round).andExpect(jsonPath("$.data.canUseDefense").value(false));
        defend(userA, round).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("DEFENSE_009"));
    }

    @Test
    void 발표_전_회차에는_방어권을_쓸_수_없다() throws Exception {
        giveTickets(userA, 1);
        Round voting = startVoting(party, NOV_4, true);

        defend(userA, voting).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("RESULT_001"));
    }

    // ---------- RICE-01 번뇌 완료 보상 (목탁 수행) ----------

    @Test
    void RICE_01_10_번뇌_완료_보상() throws Exception {
        Round round = pickedA(party);
        tap(userB, round, 5).andExpect(status().isCreated());

        moktak(userA, round, 5).andExpect(status().isCreated()).andExpect(jsonPath("$.data.riceEarned").value(10));

        assertThat(beonnoe(userA)).singleElement().satisfies(history -> {
            assertThat(history.getDelta()).isEqualTo(10);
            assertThat(history.getRoom().getId()).isEqualTo(party.room().getId());
        });
        // 다시 보내도 보상은 한 번이다.
        moktak(userA, round, 5).andExpect(status().isCreated()).andExpect(jsonPath("$.data.riceEarned").value(0));
        assertThat(beonnoe(userA)).hasSize(1);
    }

    @Test
    void RICE_01_11_방어하면_번뇌_보상_없음() throws Exception {
        giveTickets(userA, 1);
        Round round = pickedA(party);
        tap(userB, round, 5).andExpect(status().isCreated());
        defend(userA, round).andExpect(status().isCreated());

        moktak(userA, round, 5).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("MOKTAK_001"));

        assertThat(beonnoe(userA)).isEmpty();
    }

    @Test
    void RICE_01_13_받은_톡톡이_0번이면_번뇌_보상_없음() throws Exception {
        Round round = pickedA(party);
        tap(userB, round, 0).andExpect(status().isCreated());

        moktak(userA, round, 5).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("MOKTAK_002"));

        assertThat(beonnoe(userA)).isEmpty();
    }

    @Test
    void RICE_01_14_다음_투표가_열린_뒤에는_번뇌_보상_없음() throws Exception {
        Round round = pickedA(party);
        tap(userB, round, 5).andExpect(status().isCreated());
        startVoting(party, NOV_5, true);

        moktak(userA, round, 5).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("MOKTAK_003"));

        assertThat(beonnoe(userA)).isEmpty();
    }

    @Test
    void RICE_01_15_받은_톡톡보다_적게_두드리면_번뇌_보상_없음() throws Exception {
        Round round = pickedA(party);
        tap(userB, round, 5).andExpect(status().isCreated());
        tap(userC(), round, 3).andExpect(status().isCreated());

        moktak(userA, round, 5).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("MOKTAK_004"));
        assertThat(beonnoe(userA)).isEmpty();

        moktak(userA, round, 8).andExpect(status().isCreated()).andExpect(jsonPath("$.data.riceEarned").value(10));
    }

    @Test
    void 두드린_횟수가_없으면_목탁_수행_완료를_거부한다() throws Exception {
        Round round = pickedA(party);
        tap(userB, round, 5).andExpect(status().isCreated());

        mockMvc.perform(post("/api/rounds/{roundId}/moktak", round.getId())
                        .header("Authorization", bearer(userA))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON_002"));
    }

    @Test
    void 지목된_사람이_아니면_목탁_수행을_완료할_수_없다() throws Exception {
        Round round = pickedA(party);
        tap(userC(), round, 5).andExpect(status().isCreated());

        moktak(userB, round, 5).andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("TAP_005"));
    }

    // ---------- 준비 ----------

    private User userC() {
        return party.c().getUser();
    }

    /** B·C·D가 A를, A가 B를 골라 A가 지목된 11/4 회차. A는 못 참았어요로 인증한다. */
    private Round pickedA(Party target) {
        return announced(target, NOV_4, true);
    }

    private Round announced(Party target, LocalDate date, boolean aCertifies) {
        Round round = startVoting(target, date, aCertifies);
        voteRepository.save(Vote.create(reload(round), target.b(), target.a()));
        voteRepository.save(Vote.create(reload(round), target.c(), target.a()));
        voteRepository.save(Vote.create(reload(round), target.d(), target.a()));
        voteRepository.save(Vote.create(reload(round), target.a(), target.b()));
        voteCloseService.closeAtDeadline(target.room().getId(), round.getId(), date.plusDays(1).atTime(15, 0));
        Round closed = reload(round);
        assertThat(closed.getStatus()).isEqualTo(RoundStatus.ANNOUNCED);
        assertThat(closed.getPickedMember().getId()).isEqualTo(target.a().getId());
        return closed;
    }

    private Round startVoting(Party target, LocalDate date, boolean aCertifies) {
        Round round = roundRepository.save(Round.create(target.room(), date));
        for (RoomMember member : List.of(target.a(), target.b(), target.c(), target.d())) {
            boolean isA = member.getId().equals(target.a().getId());
            if (isA && !aCertifies) {
                continue;
            }
            certificationRepository.save(Certification.create(round, member,
                    isA ? CertificationResult.FAILED : CertificationResult.ENDURED, "기록",
                    isA ? 23000 : null, date.atTime(20, 0)));
        }
        voteStartService.startAtDeadline(target.room().getId(), round.getId(), date.plusDays(1).atTime(3, 0));
        return reload(round);
    }

    private Party newParty(User b) {
        Room room = roomRepository.save(Room.create("배달 끊기 방", RoomGoal.DELIVERY, 4));
        return new Party(room,
                addMember(room, userA, "민지", BosalColor.PINK),
                addMember(room, b, "지수", BosalColor.PURPLE),
                addMember(room, newUser(), "하린", BosalColor.MINT),
                addMember(room, newUser(), "도윤", BosalColor.YELLOW));
    }

    private void earn(User user, int amount) {
        riceHistoryRepository.save(RiceHistory.create(
                user, null, amount, RiceReason.CERTIFICATION, RiceRefType.CERTIFICATION, refSequence++));
    }

    private void giveTickets(User user, int count) {
        for (int i = 0; i < count; i++) {
            defenseTicketRepository.save(DefenseTicket.purchase(user, AFTER_CLOSE.minusDays(1).plusMinutes(i)));
        }
    }

    private long ticketCount(User user) {
        return defenseTicketRepository.countByUser_IdAndUsedAtIsNull(user.getId());
    }

    private long balance(User user) {
        return riceHistoryRepository.sumDeltaByUserId(user.getId());
    }

    private List<RiceHistory> beonnoe(User user) {
        return riceHistoryRepository.findAll().stream()
                .filter(history -> history.getUser().getId().equals(user.getId())
                        && history.getReason() == RiceReason.BEONNOE)
                .toList();
    }

    private ResultActions buy(User user) throws Exception {
        return mockMvc.perform(post("/api/defense-tickets").header("Authorization", bearer(user)));
    }

    private ResultActions defend(User user, Round round) throws Exception {
        return mockMvc.perform(post("/api/rounds/{roundId}/defenses", round.getId()).header("Authorization", bearer(user)));
    }

    private ResultActions moktak(User user, Round round, int count) throws Exception {
        return mockMvc.perform(post("/api/rounds/{roundId}/moktak", round.getId())
                .header("Authorization", bearer(user))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"count\": " + count + "}"));
    }

    private ResultActions result(User user, Round round) throws Exception {
        return mockMvc.perform(get("/api/rounds/{roundId}/result", round.getId()).header("Authorization", bearer(user)))
                .andExpect(status().isOk());
    }

    private ResultActions tap(User user, Round round, int count) throws Exception {
        return mockMvc.perform(post("/api/rounds/{roundId}/taps", round.getId())
                .header("Authorization", bearer(user))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"count\": " + count + "}"));
    }

    private interface Call {
        ResultActions run() throws Exception;
    }

    private static int statusOf(Call call) {
        try {
            return call.run().andReturn().getResponse().getStatus();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static List<Integer> concurrently(Supplier<Integer> first, Supplier<Integer> second) {
        CountDownLatch ready = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            CompletableFuture<Integer> one = CompletableFuture.supplyAsync(() -> {
                await(ready);
                return first.get();
            }, executor);
            CompletableFuture<Integer> two = CompletableFuture.supplyAsync(() -> {
                await(ready);
                return second.get();
            }, executor);
            ready.countDown();
            return List.of(one.join(), two.join());
        } finally {
            executor.shutdown();
        }
    }

    private static void await(CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private String bearer(User user) {
        return "Bearer " + jwtProvider.createAccessToken(user.getId());
    }

    private Round reload(Round round) {
        return roundRepository.findById(round.getId()).orElseThrow();
    }

    private User newUser() {
        return userRepository.save(User.create("anon-defense-" + userSequence++));
    }

    private RoomMember addMember(Room room, User user, String nickname, BosalColor color) {
        return roomMemberRepository.save(RoomMember.create(room, user, nickname, color, NOV_4.minusDays(10).atStartOfDay()));
    }
}
