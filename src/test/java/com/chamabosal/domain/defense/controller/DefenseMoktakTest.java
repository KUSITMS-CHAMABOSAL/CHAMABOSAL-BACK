package com.chamabosal.domain.defense.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.chamabosal.domain.room.service.RoomService;
import com.chamabosal.domain.certification.entity.Certification;
import com.chamabosal.domain.certification.entity.CertificationResult;
import com.chamabosal.domain.certification.repository.CertificationRepository;
import com.chamabosal.domain.defense.entity.DefenseTicket;
import com.chamabosal.domain.defense.repository.DefenseTicketRepository;
import com.chamabosal.domain.pot.entity.RoomPot;
import com.chamabosal.domain.pot.repository.PotContributionRepository;
import com.chamabosal.domain.pot.repository.RoomPotRepository;
import com.chamabosal.domain.reaction.repository.ReactionRepository;
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
import com.chamabosal.domain.tap.repository.TapStartRepository;
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
    /** 11/4 회차는 11/5 15:00에 마감(결과 발표)된다. 방어권은 15:02까지 쓸 수 있다. */
    private static final LocalDateTime AFTER_CLOSE = NOV_5.atTime(15, 0);
    /** 11/4 회차는 11/5 15:00에 마감돼 5시간 뒤인 20:00에 톡톡이 전달되고 목탁 수행을 할 수 있다. */
    private static final LocalDateTime DELIVERED = NOV_5.atTime(20, 0);

    @Autowired MockMvc mockMvc;
    @Autowired MutableClock clock;
    @Autowired JwtProvider jwtProvider;
    @Autowired VoteStartService voteStartService;
    @Autowired VoteCloseService voteCloseService;
    @Autowired UserRepository userRepository;
    @Autowired RoomRepository roomRepository;
    @Autowired RoomMemberRepository roomMemberRepository;
    @Autowired ReactionRepository reactionRepository;
    @Autowired PotContributionRepository potContributionRepository;
    @Autowired RoomPotRepository roomPotRepository;
    @Autowired RoundRepository roundRepository;
    @Autowired RoundParticipantRepository roundParticipantRepository;
    @Autowired CertificationRepository certificationRepository;
    @Autowired VoteRepository voteRepository;
    @Autowired TapRepository tapRepository;
    @Autowired TapStartRepository tapStartRepository;
    @Autowired RiceHistoryRepository riceHistoryRepository;
    @Autowired DefenseTicketRepository defenseTicketRepository;
    @Autowired RoomService roomService;

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
        reactionRepository.deleteAll();
        defenseTicketRepository.deleteAll();
        riceHistoryRepository.deleteAll();
        tapStartRepository.deleteAll();
        tapRepository.deleteAll();
        voteRepository.deleteAll();
        roundParticipantRepository.deleteAll();
        certificationRepository.deleteAll();
        roundRepository.deleteAll();
        potContributionRepository.deleteAll();
        roomPotRepository.deleteAll();
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
    void RESULT_01_8_단독_지목된_사람이_1분_안에_방어하면_DEFENDED이고_다른_사람은_지목되지_않는다() throws Exception {
        giveTickets(userA, 1);
        Round round = pickedA(party);
        result(userA, round);

        clock.set(AFTER_CLOSE.plusMinutes(1));
        defend(userA, round).andExpect(status().isCreated());

        Round defended = reload(round);
        assertThat(defended.getResultType()).isEqualTo(RoundResultType.DEFENDED);
        assertThat(defended.getPickedMember().getId()).isEqualTo(party.a().getId());
    }

    @Test
    void RESULT_01_9_동점으로_모두_무사면_방어할_사람이_없어_DEFENSE_008이다() throws Exception {
        giveTickets(userA, 1);
        Round round = startVoting(party, NOV_4, true);
        voteRepository.save(Vote.create(reload(round), party.b(), party.a()));
        voteRepository.save(Vote.create(reload(round), party.c(), party.a()));
        voteRepository.save(Vote.create(reload(round), party.d(), party.b()));
        voteRepository.save(Vote.create(reload(round), party.a(), party.b()));
        voteCloseService.closeAtDeadline(party.room().getId(), round.getId(), NOV_5.atTime(15, 0));
        assertThat(reload(round).getResultType()).isEqualTo(RoundResultType.ALL_SAFE);
        result(userA, round);

        defend(userA, round).andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("DEFENSE_008"));

        assertThat(reload(round).getResultType()).isEqualTo(RoundResultType.ALL_SAFE);
        assertThat(ticketCount(userA)).isEqualTo(1);
    }

    @Test
    void RESULT_01_10_결과_응답에는_누가_누구를_골랐는지_없다() throws Exception {
        Round round = pickedA(party);

        String body = result(userB, round).andReturn().getResponse().getContentAsString();

        assertThat(body).doesNotContain("voter").doesNotContain("\"votes\"");
        for (RoomMember member : List.of(party.a(), party.b(), party.c(), party.d())) {
            result(member.getUser(), round)
                    .andExpect(jsonPath("$.data.myVote").doesNotExist())
                    .andExpect(jsonPath("$.data.voteCounts[*].voterMemberId").isEmpty());
        }
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

    /** 결과를 열지 않고 방어를 눌러 미인증으로 거절돼도 그 시각은 처음 연 시각으로 남아, 2분 뒤에는 고민 중이 아니다 (코드리뷰 반영). */
    @Test
    void 미인증으로_거절된_방어_요청도_처음_연_시각을_남긴다() throws Exception {
        giveTickets(userA, 1);
        Round round = announced(party, NOV_4, false);

        clock.set(NOV_5.atTime(16, 0));
        defend(userA, round).andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("DEFENSE_011"));

        assertThat(reload(round).getPickedSeenAt()).isEqualTo(NOV_5.atTime(16, 0));
        clock.set(NOV_5.atTime(16, 3));
        result(userB, round).andExpect(jsonPath("$.data.isDefensePending").value(false));
    }

    /** 방어 요청은 이미 남은 처음 연 시각을 덮어쓰지 않는다 (코드리뷰 반영). */
    @Test
    void 방어_요청은_먼저_남은_처음_연_시각을_바꾸지_않는다() throws Exception {
        giveTickets(userA, 1);
        Round round = pickedA(party);
        clock.set(NOV_5.atTime(16, 0));
        result(userA, round);

        clock.set(NOV_5.atTime(16, 1));
        defend(userA, round).andExpect(status().isCreated());

        assertThat(reload(round).getPickedSeenAt()).isEqualTo(NOV_5.atTime(16, 0));
    }

    @Test
    void SHIELD_02_6_인증하지_않은_지목자는_방어권을_쓸_수_없다() throws Exception {
        giveTickets(userA, 1);
        Round round = announced(party, NOV_4, false);

        clock.set(AFTER_CLOSE.plusMinutes(1));
        result(userA, round).andExpect(jsonPath("$.data.canUseDefense").value(false));
        defend(userA, round).andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("DEFENSE_011"));

        assertThat(reload(round).getResultType()).isEqualTo(RoundResultType.PICKED);
        assertThat(ticketCount(userA)).isEqualTo(1);
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
        clock.set(DELIVERED);
        moktak(userA, round, 5).andExpect(status().isCreated());

        clock.set(DELIVERED.plusMinutes(1));
        defend(userA, round).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("DEFENSE_010"));

        assertThat(reload(round).getResultType()).isEqualTo(RoundResultType.PICKED);
        assertThat(beonnoe(userA)).hasSize(1);
        assertThat(ticketCount(userA)).isEqualTo(1);
    }

    @Test
    void RESULT_02_6_톡톡_마감_뒤_처음_조회하면_방어권을_쓸_수_없다() throws Exception {
        giveTickets(userA, 1);
        Round round = pickedA(party);

        clock.set(DELIVERED);
        result(userA, round)
                .andExpect(jsonPath("$.data.defenseUsableUntil").isEmpty())
                .andExpect(jsonPath("$.data.canUseDefense").value(false));
        defend(userA, round).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("DEFENSE_009"));
    }

    @Test
    void SHIELD_02_13_늦게_처음_열어도_그때부터_2분_동안_쓸_수_있다() throws Exception {
        giveTickets(userA, 1);
        Round round = pickedA(party);

        clock.set(NOV_5.atTime(19, 0));
        result(userA, round).andExpect(jsonPath("$.data.defenseUsableUntil").value("2026-11-05T19:02:00"));
        clock.set(NOV_5.atTime(19, 1));
        defend(userA, round).andExpect(status().isCreated());

        assertThat(reload(round).getResultType()).isEqualTo(RoundResultType.DEFENDED);
    }

    @Test
    void SHIELD_02_14_톡톡_마감_뒤에_처음_열면_방어권을_쓸_수_없다() throws Exception {
        giveTickets(userA, 1);
        Round round = pickedA(party);

        clock.set(DELIVERED);
        defend(userA, round).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("DEFENSE_009"));

        assertThat(ticketCount(userA)).isEqualTo(1);
    }

    @Test
    void RESULT_02_9_지목된_사람이_열기_전과_연_뒤_2분_동안만_방어권_고민_중이다() throws Exception {
        Round round = pickedA(party);

        clock.set(NOV_5.atTime(16, 0));
        result(userB, round).andExpect(jsonPath("$.data.isDefensePending").value(true));
        result(userA, round);
        clock.set(NOV_5.atTime(16, 1));
        result(userB, round).andExpect(jsonPath("$.data.isDefensePending").value(true));
        clock.set(NOV_5.atTime(16, 3));
        result(userB, round).andExpect(jsonPath("$.data.isDefensePending").value(false));
    }

    @Test
    void RESULT_03_20_방어_전에_시작한_톡톡은_방어_뒤에도_보내고_5석을_받지만_전달하지_않는다() throws Exception {
        giveTickets(userA, 1);
        Round round = pickedA(party);
        tapStart(userB, round).andExpect(status().isCreated());
        tapStart(userB, round).andExpect(status().isOk());
        defend(userA, round).andExpect(status().isCreated());

        tap(userB, round, 7).andExpect(status().isCreated()).andExpect(jsonPath("$.data.riceEarned").value(5));
        tapStart(userC(), round).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("TAP_001"));

        clock.set(DELIVERED);
        received(userA, round).andExpect(jsonPath("$.data.taps").isEmpty());
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
        clock.set(DELIVERED);

        moktak(userA, round, 5).andExpect(status().isCreated()).andExpect(jsonPath("$.data.riceEarned").value(2));

        assertThat(beonnoe(userA)).singleElement().satisfies(history -> {
            assertThat(history.getDelta()).isEqualTo(2);
            assertThat(history.getRoom().getId()).isEqualTo(party.room().getId());
        });
        // 다시 보내도 보상은 한 번이다.
        moktak(userA, round, 5).andExpect(status().isCreated()).andExpect(jsonPath("$.data.riceEarned").value(0));
        assertThat(beonnoe(userA)).hasSize(1);
    }

    @Test
    void POT_01_9_톡톡_완료와_리액션도_한_솥에_쌓인다() throws Exception {
        Round round = pickedA(party);
        RoomPot pot = RoomPot.first(party.room());
        pot.add(100);
        roomPotRepository.save(pot);
        Round today = roundRepository.save(Round.create(party.room(), NOV_5));
        Certification certificationC = certificationRepository.save(Certification.create(
                today, party.c(), CertificationResult.ENDURED, "기록", null, AFTER_CLOSE));

        tap(userB, round, 1).andExpect(status().isCreated());
        mockMvc.perform(put("/api/certifications/{certificationId}/reactions/me", certificationC.getId())
                        .header("Authorization", bearer(party.d().getUser()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\": \"GOOD\"}"))
                .andExpect(status().isOk());

        assertThat(roomPotRepository.findFirstByRoom_IdOrderBySeqDesc(party.room().getId()).orElseThrow().getAmount())
                .isEqualTo(106);
    }

    @Test
    void POT_01_10_번뇌_완료는_한_솥에_넣지_않는다() throws Exception {
        Round round = pickedA(party);
        tap(userB, round, 5).andExpect(status().isCreated());
        int before = roomPotRepository.findFirstByRoom_IdOrderBySeqDesc(party.room().getId()).orElseThrow().getAmount();
        clock.set(DELIVERED);

        moktak(userA, round, 5).andExpect(status().isCreated()).andExpect(jsonPath("$.data.riceEarned").value(2));

        assertThat(roomPotRepository.findFirstByRoom_IdOrderBySeqDesc(party.room().getId()).orElseThrow().getAmount())
                .isEqualTo(before);
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
        clock.set(DELIVERED);

        moktak(userA, round, 5).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("MOKTAK_002"));

        assertThat(beonnoe(userA)).isEmpty();
    }

    @Test
    void RICE_01_14_다음_투표가_열린_뒤에는_번뇌_보상_없음() throws Exception {
        Round round = pickedA(party);
        tap(userB, round, 5).andExpect(status().isCreated());
        startVoting(party, NOV_5, true);
        clock.set(DELIVERED);

        moktak(userA, round, 5).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("MOKTAK_003"));

        assertThat(beonnoe(userA)).isEmpty();
    }

    @Test
    void RICE_01_15_받은_톡톡보다_적게_두드리면_번뇌_보상_없음() throws Exception {
        Round round = pickedA(party);
        tap(userB, round, 5).andExpect(status().isCreated());
        tap(userC(), round, 3).andExpect(status().isCreated());
        clock.set(DELIVERED);

        moktak(userA, round, 5).andExpect(status().isCreated()).andExpect(jsonPath("$.data.riceEarned").value(0));
        assertThat(beonnoe(userA)).isEmpty();
        assertThat(reload(round).isMoktakEnded()).isTrue();

        // 끝난 회차라 다 두드려 다시 보내도 보상이 없다.
        moktak(userA, round, 8).andExpect(status().isCreated()).andExpect(jsonPath("$.data.riceEarned").value(0));
        assertThat(beonnoe(userA)).isEmpty();
    }

    @Test
    void RESULT_03_18_종료하기로_끝내면_받은_톡톡에_끝냄으로_나오고_다시_못_한다() throws Exception {
        Round round = pickedA(party);
        tap(userB, round, 5).andExpect(status().isCreated());
        tap(userC(), round, 3).andExpect(status().isCreated());
        clock.set(DELIVERED);

        moktak(userA, round, 5).andExpect(status().isCreated()).andExpect(jsonPath("$.data.riceEarned").value(0));

        received(userA, round).andExpect(jsonPath("$.data.isMoktakDone").value(true));
        moktak(userA, round, 8).andExpect(jsonPath("$.data.riceEarned").value(0));
    }

    // ---------- RESULT-03 전원이 보내면 일찍 전달 ----------

    @Test
    void RESULT_03_22_전원이_보내면_방어_시간이_지난_뒤_바로_전달된다() throws Exception {
        Round round = pickedA(party);
        result(userA, round);
        clock.set(AFTER_CLOSE.plusMinutes(1));
        tapAll(round, 5, 3, 2);

        clock.set(AFTER_CLOSE.plusMinutes(2));
        received(userA, round).andExpect(jsonPath("$.data.isDelivered").value(false));
        moktak(userA, round, 10).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("MOKTAK_005"));

        clock.set(AFTER_CLOSE.plusMinutes(2).plusSeconds(1));
        received(userA, round)
                .andExpect(jsonPath("$.data.isDelivered").value(true))
                .andExpect(jsonPath("$.data.deliveredAt").value("2026-11-05T15:02:00"))
                .andExpect(jsonPath("$.data.totalCount").value(10));
        moktak(userA, round, 10).andExpect(status().isCreated()).andExpect(jsonPath("$.data.riceEarned").value(2));
    }

    @Test
    void RESULT_03_23_방어_시간_뒤_마지막_사람이_보내면_그_순간_전달된다() throws Exception {
        Round round = pickedA(party);
        result(userA, round);
        tap(userB, round, 5).andExpect(status().isCreated());
        tap(userC(), round, 3).andExpect(status().isCreated());

        clock.set(AFTER_CLOSE.plusMinutes(10));
        tap(party.d().getUser(), round, 2).andExpect(status().isCreated());

        received(userA, round)
                .andExpect(jsonPath("$.data.isDelivered").value(true))
                .andExpect(jsonPath("$.data.deliveredAt").value("2026-11-05T15:10:00"));
    }

    @Test
    void RESULT_03_24_전원이_보냈어도_지목된_사람이_결과를_열지_않으면_전달되지_않는다() throws Exception {
        Round round = pickedA(party);
        clock.set(AFTER_CLOSE.plusMinutes(5));
        tapAll(round, 5, 3, 2);

        clock.set(NOV_5.atTime(16, 0));
        received(userA, round).andExpect(jsonPath("$.data.isDelivered").value(false));

        result(userA, round);
        clock.set(NOV_5.atTime(16, 2));
        received(userA, round).andExpect(jsonPath("$.data.isDelivered").value(false));
        clock.set(NOV_5.atTime(16, 2, 1));
        received(userA, round)
                .andExpect(jsonPath("$.data.isDelivered").value(true))
                .andExpect(jsonPath("$.data.deliveredAt").value("2026-11-05T16:02:00"));
    }

    @Test
    void RESULT_03_25_한_명이라도_안_보내면_5시간까지_전달되지_않는다() throws Exception {
        Round round = pickedA(party);
        result(userA, round);
        tap(userB, round, 5).andExpect(status().isCreated());
        tap(userC(), round, 3).andExpect(status().isCreated());

        clock.set(DELIVERED.minusMinutes(1));
        received(userA, round).andExpect(jsonPath("$.data.isDelivered").value(false));
        clock.set(DELIVERED);
        received(userA, round)
                .andExpect(jsonPath("$.data.isDelivered").value(true))
                .andExpect(jsonPath("$.data.deliveredAt").value("2026-11-05T20:00:00"));
    }

    @Test
    void RESULT_03_26_안_보낸_사람이_나가면_그_순간_전달된다() throws Exception {
        Round round = pickedA(party);
        result(userA, round);
        tap(userB, round, 5).andExpect(status().isCreated());
        tap(userC(), round, 3).andExpect(status().isCreated());

        clock.set(AFTER_CLOSE.plusMinutes(10));
        roomService.leave(party.d().getUser().getId(), party.room().getId());

        received(userA, round)
                .andExpect(jsonPath("$.data.isDelivered").value(true))
                .andExpect(jsonPath("$.data.deliveredAt").value("2026-11-05T15:10:00"));
    }

    @Test
    void 안_보낸_사람이_내보내져도_그_순간_전달된다() throws Exception {
        Round round = pickedA(party);
        result(userA, round);
        tap(userB, round, 5).andExpect(status().isCreated());
        tap(userC(), round, 3).andExpect(status().isCreated());
        Room room = roomRepository.findById(party.room().getId()).orElseThrow();
        room.assignOwner(party.b().getId());
        roomRepository.save(room);

        clock.set(AFTER_CLOSE.plusMinutes(10));
        roomService.kick(userB.getId(), party.room().getId(), party.d().getId());

        received(userA, round).andExpect(jsonPath("$.data.isDelivered").value(true));
    }

    @Test
    void RESULT_03_27_건너뛰기도_보낸_것으로_센다() throws Exception {
        Round round = pickedA(party);
        result(userA, round);
        tapAll(round, 5, 3, 0);

        clock.set(AFTER_CLOSE.plusMinutes(3));
        received(userA, round)
                .andExpect(jsonPath("$.data.isDelivered").value(true))
                .andExpect(jsonPath("$.data.taps.length()").value(2))
                .andExpect(jsonPath("$.data.totalCount").value(8));
    }

    @Test
    void RESULT_03_28_방어하면_전원이_보냈어도_받은_톡톡과_목탁이_없다() throws Exception {
        giveTickets(userA, 1);
        Round round = pickedA(party);
        result(userA, round);
        tapAll(round, 5, 3, 2);

        clock.set(AFTER_CLOSE.plusMinutes(1));
        defend(userA, round).andExpect(status().isCreated());

        clock.set(AFTER_CLOSE.plusMinutes(3));
        received(userA, round).andExpect(jsonPath("$.data.taps").isEmpty());
        moktak(userA, round, 10).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("MOKTAK_001"));
    }

    @Test
    void RESULT_03_29_마지막_두_명이_동시에_보내도_전원이_보낸_시각은_하나다() {
        Round round = pickedA(party);
        statusOf(() -> tap(userB, round, 5));

        List<Integer> statuses = concurrently(
                () -> statusOf(() -> tap(userC(), round, 3)),
                () -> statusOf(() -> tap(party.d().getUser(), round, 2)));

        assertThat(statuses).containsOnly(201);
        assertThat(reload(round).getTapsCompletedAt()).isEqualTo(AFTER_CLOSE);
    }

    @Test
    void RESULT_03_30_지목된_사람이_나간_결과는_전원이_보낸_시각을_남기지_않는다() throws Exception {
        Round round = pickedA(party);
        tap(userB, round, 5).andExpect(status().isCreated());
        tap(userC(), round, 3).andExpect(status().isCreated());
        roomService.leave(userA.getId(), party.room().getId());

        roomService.leave(party.d().getUser().getId(), party.room().getId());

        assertThat(reload(round).getTapsCompletedAt()).isNull();
    }

    @Test
    void VOTE_05_24_전원이_톡톡하면_방어_시간_뒤_지목된_사람의_투표_탭은_목탁_수행이다() throws Exception {
        Round round = pickedA(party);
        result(userA, round);
        seenResult(userA, round);
        tapAll(round, 5, 3, 2);

        clock.set(AFTER_CLOSE.plusMinutes(3));
        voteTab(userA, party)
                .andExpect(jsonPath("$.data.status").value("MOKTAK"))
                .andExpect(jsonPath("$.data.moktakCount").value(10));
    }

    @Test
    void RICE_01_16_전달_전에는_번뇌_보상_없음() throws Exception {
        Round round = pickedA(party);
        tap(userB, round, 5).andExpect(status().isCreated());
        clock.set(DELIVERED.minusSeconds(1));

        moktak(userA, round, 5).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("MOKTAK_005"));

        assertThat(beonnoe(userA)).isEmpty();
    }

    @Test
    void RESULT_03_17_목탁_수행을_마치면_받은_톡톡에_완료로_나온다() throws Exception {
        Round round = pickedA(party);
        tap(userB, round, 5).andExpect(status().isCreated());
        clock.set(DELIVERED);
        received(userA, round).andExpect(jsonPath("$.data.isMoktakDone").value(false));

        moktak(userA, round, 5).andExpect(status().isCreated());

        received(userA, round)
                .andExpect(jsonPath("$.data.isMoktakDone").value(true))
                .andExpect(jsonPath("$.data.totalCount").value(5));
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
        Room room = roomRepository.save(Room.create("배달 끊기 방", RoomGoal.DELIVERY, 4, 300));
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

    private ResultActions received(User user, Round round) throws Exception {
        return mockMvc.perform(get("/api/rounds/{roundId}/taps/received", round.getId())
                .header("Authorization", bearer(user)));
    }

    private ResultActions moktak(User user, Round round, int count) throws Exception {
        return mockMvc.perform(post("/api/rounds/{roundId}/moktak", round.getId())
                .header("Authorization", bearer(user))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"count\": " + count + "}"));
    }

    /** B·C·D가 차례로 보낸다. 0번은 건너뛰기다. */
    private void tapAll(Round round, int b, int c, int d) throws Exception {
        tap(userB, round, b).andExpect(status().isCreated());
        tap(userC(), round, c).andExpect(status().isCreated());
        tap(party.d().getUser(), round, d).andExpect(status().isCreated());
    }

    private void seenResult(User user, Round round) throws Exception {
        mockMvc.perform(put("/api/rooms/{roomId}/members/me/seen-result", round.getRoom().getId())
                        .header("Authorization", bearer(user))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"roundId\": " + round.getId() + "}"))
                .andExpect(status().isOk());
    }

    private ResultActions voteTab(User user, Party target) throws Exception {
        return mockMvc.perform(get("/api/rooms/{roomId}/vote", target.room().getId()).header("Authorization", bearer(user)));
    }

    private ResultActions result(User user, Round round) throws Exception {
        return mockMvc.perform(get("/api/rounds/{roundId}/result", round.getId()).header("Authorization", bearer(user)))
                .andExpect(status().isOk());
    }

    private ResultActions tapStart(User user, Round round) throws Exception {
        return mockMvc.perform(post("/api/rounds/{roundId}/taps/start", round.getId()).header("Authorization", bearer(user)));
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
