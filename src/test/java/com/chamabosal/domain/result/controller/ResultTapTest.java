package com.chamabosal.domain.result.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.chamabosal.domain.certification.entity.Certification;
import com.chamabosal.domain.certification.entity.CertificationResult;
import com.chamabosal.domain.certification.repository.CertificationRepository;
import com.chamabosal.domain.certification.service.LeftMemberCleanupScheduler;
import com.chamabosal.domain.defense.entity.DefenseTicket;
import com.chamabosal.domain.defense.repository.DefenseTicketRepository;
import com.chamabosal.domain.pot.repository.PotContributionRepository;
import com.chamabosal.domain.pot.repository.RoomPotRepository;
import com.chamabosal.domain.rice.entity.RiceReason;
import com.chamabosal.domain.rice.repository.RiceHistoryRepository;
import com.chamabosal.domain.room.entity.BosalColor;
import com.chamabosal.domain.room.entity.LeaveType;
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
import com.chamabosal.domain.store.entity.Item;
import com.chamabosal.domain.store.entity.ItemSlot;
import com.chamabosal.domain.store.entity.UserItem;
import com.chamabosal.domain.store.repository.ItemRepository;
import com.chamabosal.domain.store.repository.UserItemRepository;
import com.chamabosal.domain.tap.entity.Tap;
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
import java.util.Map;
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
import org.springframework.test.web.servlet.ResultActions;

/**
 * 결과 화면(RESULT-02), 이마 톡톡(RESULT-03), 결과 확인 저장(ROOM-06). 공통 Given: 방 인원은 A, B, C, D 4명이다.
 * 발표된 회차는 실제 흐름(인증 → 다음 날 03:00 투표 시작 → 표 → 다음 날 15:00 마감)으로 만든다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(MutableClock.Config.class)
class ResultTapTest {

    private static final LocalDate NOV_2 = LocalDate.of(2026, 11, 2);
    private static final LocalDate NOV_3 = LocalDate.of(2026, 11, 3);
    private static final LocalDate NOV_4 = LocalDate.of(2026, 11, 4);
    private static final LocalDate NOV_5 = LocalDate.of(2026, 11, 5);
    private static final LocalDateTime AFTER_CLOSE = NOV_4.plusDays(1).atTime(16, 0);
    /** 11/4 회차는 11/5 15:00에 마감돼 5시간 뒤인 20:00에 톡톡이 전달된다. */
    private static final LocalDateTime DELIVERED = NOV_5.atTime(20, 0);

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
    RoomService roomService;

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
    VoteRepository voteRepository;

    @Autowired
    TapRepository tapRepository;

    @Autowired
    RiceHistoryRepository riceHistoryRepository;

    @Autowired
    DefenseTicketRepository defenseTicketRepository;

    @Autowired
    LeftMemberCleanupScheduler leftMemberCleanupScheduler;

    @Autowired
    ItemRepository itemRepository;

    @Autowired
    UserItemRepository userItemRepository;

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
        clock.set(AFTER_CLOSE);
        room = newRoom();
        userA = newUser();
        userB = newUser();
        userC = newUser();
        userD = newUser();
        memberA = addMember(room, userA, "민지", BosalColor.PINK);
        memberB = addMember(room, userB, "지수", BosalColor.PURPLE);
        memberC = addMember(room, userC, "하린", BosalColor.MINT);
        memberD = addMember(room, userD, "도윤", BosalColor.YELLOW);
    }

    @AfterEach
    void tearDown() {
        userItemRepository.deleteAll();
        // 시작 때 넣은 카탈로그는 남기고 테스트가 만든 아이템만 지운다.
        itemRepository.findByCode("EQUIP_HAT").ifPresent(itemRepository::delete);
        defenseTicketRepository.deleteAll();
        riceHistoryRepository.deleteAll();
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

    // ---------- RESULT-02 결과 화면 ----------

    @Test
    void RESULT_02_1_안_본_결과가_여럿이면_가장_최근_하나만_보이고_확인하면_이전_회차도_본_것이다() throws Exception {
        announced(NOV_2, picks(memberB, memberA, memberC, memberA, memberD, memberA, memberA, memberB));
        announced(NOV_3, picks(memberB, memberA, memberC, memberA, memberD, memberA, memberA, memberB));
        Round wednesday = announced(NOV_4, picks(memberB, memberA, memberC, memberA, memberD, memberA, memberA, memberB));

        voteTab(userB).andExpect(jsonPath("$.data.status").value("RESULT_UNSEEN"))
                .andExpect(jsonPath("$.data.roundId").value(wednesday.getId()));

        seenResult(userB, room, wednesday.getId()).andExpect(status().isOk());

        voteTab(userB).andExpect(jsonPath("$.data.status").value("RESULT_SEEN"));
        assertThat(reload(memberB).getSeenResultRoundDate()).isEqualTo(NOV_4);
    }

    @Test
    void RESULT_02_2_지목된_사람을_뺀_멤버_중_투표한_사람만_톡톡_기회가_있다() throws Exception {
        // B는 투표하지 않는다. C, D가 A를 골라 A가 지목된다.
        Round round = announced(NOV_4, picks(memberC, memberA, memberD, memberA, memberA, memberC));

        for (User user : new User[] {userC, userD}) {
            result(user, round).andExpect(jsonPath("$.data.resultType").value("PICKED"))
                    .andExpect(jsonPath("$.data.canTap").value(true));
        }
        result(userB, round).andExpect(jsonPath("$.data.canTap").value(false));
        result(userA, round).andExpect(jsonPath("$.data.isMePicked").value(true))
                .andExpect(jsonPath("$.data.canTap").value(false));
    }

    @Test
    void RESULT_02_3_모두_보살_모두_무사_방어된_결과에는_톡톡_기회가_없다() throws Exception {
        Round allBosal = announced(NOV_2, picks(memberA, null, memberB, null, memberC, null, memberD, null));
        Round allSafe = announced(NOV_3, picks(memberA, memberB, memberB, memberA, memberC, memberA, memberD, memberB));
        Round defended = announced(NOV_4, picks(memberB, memberA, memberC, memberA, memberD, memberA, memberA, memberB));
        defend(defended);

        result(userC, allBosal).andExpect(jsonPath("$.data.resultType").value("ALL_BOSAL"))
                .andExpect(jsonPath("$.data.canTap").value(false));
        result(userC, allSafe).andExpect(jsonPath("$.data.resultType").value("ALL_SAFE"))
                .andExpect(jsonPath("$.data.canTap").value(false));
        result(userC, defended).andExpect(jsonPath("$.data.resultType").value("DEFENDED"))
                .andExpect(jsonPath("$.data.canTap").value(false));
    }

    @Test
    void RESULT_02_4_지목된_사람만_방어_마감_시각을_받는다() throws Exception {
        Round round = announced(NOV_2, picks(memberB, memberA, memberC, memberA, memberD, memberA, memberA, memberB));
        defenseTicketRepository.save(DefenseTicket.purchase(userA, NOV_2.atStartOfDay()));
        defenseTicketRepository.save(DefenseTicket.purchase(userA, NOV_2.atStartOfDay()));

        clock.set(NOV_3.atTime(15, 1));
        result(userA, round)
                .andExpect(jsonPath("$.data.defenseUsableUntil").value("2026-11-03T15:02:00"))
                .andExpect(jsonPath("$.data.defenseTicketCount").value(2))
                .andExpect(jsonPath("$.data.canUseDefense").value(true));
        result(userB, round)
                .andExpect(jsonPath("$.data.defenseUsableUntil").doesNotExist())
                .andExpect(jsonPath("$.data.defenseTicketCount").doesNotExist())
                .andExpect(jsonPath("$.data.canUseDefense").value(false));
    }

    @Test
    void RESULT_02_5_투표_마감_후_5시간이_지나_처음_열면_톡톡_시간이_지났다() throws Exception {
        Round round = pickedA();
        clock.set(DELIVERED);

        result(userC, round)
                .andExpect(jsonPath("$.data.resultType").value("PICKED"))
                .andExpect(jsonPath("$.data.isTapExpired").value(true))
                .andExpect(jsonPath("$.data.canTap").value(false));
    }

    @Test
    void RESULT_02_7_지목이_있으면_득표_수를_보여주지_않는다() throws Exception {
        Round round = announced(NOV_4, picks(memberB, memberA, memberC, memberA, memberD, null, memberA, memberB));

        result(userC, round)
                .andExpect(jsonPath("$.data.picked.memberId").value(memberA.getId()))
                .andExpect(jsonPath("$.data.picked.certificationResult").value("FAILED"))
                .andExpect(jsonPath("$.data.picked.spentAmount").value(23000))
                .andExpect(jsonPath("$.data.voteCounts").isEmpty())
                .andExpect(jsonPath("$.data.allBosalCount").isEmpty());
    }

    @Test
    void RESULT_02_8_지목이_없으면_표를_받은_멤버의_득표_수를_보여준다() throws Exception {
        Round round = announced(NOV_4, picks(memberA, memberB, memberB, memberA, memberC, memberA, memberD, memberB));

        result(userC, round)
                .andExpect(jsonPath("$.data.resultType").value("ALL_SAFE"))
                .andExpect(jsonPath("$.data.voteCounts.length()").value(2))
                .andExpect(jsonPath("$.data.voteCounts[0].memberId").value(memberA.getId()))
                .andExpect(jsonPath("$.data.voteCounts[0].count").value(2))
                .andExpect(jsonPath("$.data.voteCounts[1].memberId").value(memberB.getId()))
                .andExpect(jsonPath("$.data.allBosalCount").value(0));
    }

    @Test
    void 발표_전_회차의_결과는_볼_수_없다() throws Exception {
        Round voting = startVoting(NOV_4);

        result(userB, voting).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("RESULT_001"));
    }

    // ---------- RESULT-03 이마 톡톡 ----------

    @Test
    void RESULT_03_1_횟수_상한_없이_보낼_수_있고_재시도해도_저장된_횟수는_그대로다() throws Exception {
        Round round = pickedA();
        tap(userC, round, -1).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("COMMON_002"));
        tap(userB, round, 5000).andExpect(status().isCreated());

        tap(userB, round, 5000).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("TAP_003"));
        assertThat(tapOf(round, memberB).getCount()).isEqualTo(5000);
    }

    @Test
    void RESULT_03_2_중간에_나가_4번만_보냈으면_더_칠_수_없다() throws Exception {
        Round round = pickedA();
        tap(userB, round, 4).andExpect(status().isCreated());

        tap(userB, round, 6).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("TAP_003"));
        assertThat(tapOf(round, memberB).getCount()).isEqualTo(4);
    }

    @Test
    void RESULT_03_3_건너뛰면_0회로_끝나고_다시_칠_수_없다() throws Exception {
        Round round = pickedA();
        tap(userB, round, 0).andExpect(status().isCreated());

        tap(userB, round, 3).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("TAP_003"));
        assertThat(tapOf(round, memberB).getCount()).isZero();
    }

    @Test
    void RESULT_03_4_투표_마감_후_5시간이_지나면_보낼_수_없다() throws Exception {
        Round round = pickedA();
        clock.set(DELIVERED);

        tap(userB, round, 5).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("TAP_004"));
    }

    @Test
    void RESULT_03_5_지목된_본인과_방을_나간_사람은_보낼_수_없다() throws Exception {
        Round round = pickedA();
        tap(userA, round, 5).andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("TAP_002"));

        roomService.leave(userD.getId(), room.getId());
        tap(userD, round, 5).andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("ROOM_002"));
    }

    @Test
    void RESULT_03_6_투표하지_않은_멤버는_보낼_수_없다() throws Exception {
        // B는 투표하지 않았고, E는 투표 시작 뒤 들어왔다.
        Round round = announced(NOV_4, picks(memberC, memberA, memberD, memberA, memberA, memberC));
        User userE = newUser();
        addMember(room, userE, "은우", BosalColor.PINK, AFTER_CLOSE.minusHours(1));

        tap(userE, round, 5).andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("TAP_006"));
        tap(userB, round, 5).andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("TAP_006"));
        assertThat(tapRepository.findAll()).isEmpty();
    }

    @Test
    void RESULT_03_7_5시간이_지나면_받은_톡톡이_한_번에_전달된다() throws Exception {
        Round round = pickedA();
        tap(userB, round, 5).andExpect(status().isCreated());
        tap(userC, round, 3).andExpect(status().isCreated());
        clock.set(DELIVERED);

        received(userA, round)
                .andExpect(jsonPath("$.data.isDelivered").value(true))
                .andExpect(jsonPath("$.data.totalCount").value(8))
                .andExpect(jsonPath("$.data.taps.length()").value(2))
                .andExpect(jsonPath("$.data.taps[0].nickname").value("지수"))
                .andExpect(jsonPath("$.data.taps[0].count").value(5))
                .andExpect(jsonPath("$.data.taps[1].nickname").value("하린"));
    }

    @Test
    void RESULT_03_8_재생_완료를_저장해도_받은_톡톡_전체가_나온다() throws Exception {
        Round round = pickedA();
        tap(userB, round, 5).andExpect(status().isCreated());
        clock.set(DELIVERED);
        played(userA, round).andExpect(status().isOk());
        played(userA, round).andExpect(status().isOk());

        received(userA, round).andExpect(jsonPath("$.data.taps.length()").value(1))
                .andExpect(jsonPath("$.data.totalCount").value(5));
        assertThat(tapOf(round, memberB).getPlayedAt()).isEqualTo(DELIVERED);
    }

    @Test
    void RESULT_03_18_전달_전에_재생_완료를_보내면_아무것도_바뀌지_않는다() throws Exception {
        Round round = pickedA();
        tap(userB, round, 5).andExpect(status().isCreated());

        clock.set(DELIVERED.minusMinutes(1));
        played(userA, round).andExpect(status().isOk());

        assertThat(tapOf(round, memberB).getPlayedAt()).isNull();
        clock.set(DELIVERED);
        played(userA, round).andExpect(status().isOk());
        assertThat(tapOf(round, memberB).getPlayedAt()).isEqualTo(DELIVERED);
    }

    @Test
    void RESULT_01_11_투표_중에_나간_멤버도_지목되고_떠난_보살로_나온다() throws Exception {
        Round round = startVoting(NOV_4);
        clock.set(NOV_5.atTime(10, 0));
        roomService.leave(userA.getId(), room.getId());
        votes(round, picks(memberB, memberA, memberC, memberA, memberD, memberA));
        voteCloseService.closeAtDeadline(room.getId(), round.getId(), NOV_5.atTime(15, 0));
        clock.set(AFTER_CLOSE);

        assertThat(reload(round).getResultType()).isEqualTo(RoundResultType.PICKED);
        result(userB, round).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.picked.memberId").value(memberA.getId()))
                .andExpect(jsonPath("$.data.picked.isLeft").value(true))
                .andExpect(jsonPath("$.data.canTap").value(false));
    }

    @Test
    void RESULT_03_19_지목된_사람이_방을_나갔으면_톡톡을_보낼_수_없다() throws Exception {
        Round round = pickedA();
        roomService.leave(userA.getId(), room.getId());

        tap(userB, round, 5).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("TAP_001"));
    }

    @Test
    void RESULT_03_9_건너뛴_톡톡은_빼고_전달된다() throws Exception {
        Round round = pickedA();
        tap(userB, round, 5).andExpect(status().isCreated());
        tap(userC, round, 0).andExpect(status().isCreated());
        clock.set(DELIVERED);

        received(userA, round).andExpect(jsonPath("$.data.taps.length()").value(1))
                .andExpect(jsonPath("$.data.taps[0].nickname").value("지수"))
                .andExpect(jsonPath("$.data.totalCount").value(5));
    }

    @Test
    void RESULT_03_10_방어_시간_중에도_보낼_수_있다() throws Exception {
        Round round = pickedA();
        result(userA, round).andExpect(status().isOk());

        clock.set(AFTER_CLOSE.plusMinutes(1));
        tap(userB, round, 5).andExpect(status().isCreated());
    }

    @Test
    void RESULT_03_11_방어된_결과에는_보낼_수_없고_받은_톡톡도_비어_있다() throws Exception {
        Round round = pickedA();
        tap(userB, round, 5).andExpect(status().isCreated());
        defend(round);

        tap(userC, round, 5).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("TAP_001"));
        clock.set(DELIVERED);
        received(userA, round).andExpect(jsonPath("$.data.taps").isEmpty())
                .andExpect(jsonPath("$.data.totalCount").value(0));
    }

    @Test
    void RESULT_03_12_톡톡을_1번_이상_보내면_5석을_받는다() throws Exception {
        Round round = pickedA();

        tap(userB, round, 3).andExpect(status().isCreated()).andExpect(jsonPath("$.data.riceEarned").value(5));

        assertThat(riceHistoryRepository.findAll()).singleElement()
                .satisfies(history -> {
                    assertThat(history.getReason()).isEqualTo(RiceReason.TAP);
                    assertThat(history.getDelta()).isEqualTo(5);
                });
    }

    @Test
    void RESULT_03_13_건너뛰면_공양미가_없다() throws Exception {
        Round round = pickedA();

        tap(userB, round, 0).andExpect(status().isCreated()).andExpect(jsonPath("$.data.riceEarned").value(0));

        assertThat(riceHistoryRepository.findAll()).isEmpty();
    }

    @Test
    void RESULT_03_14_15시에_자동_마감된_결과에도_보낼_수_있다() throws Exception {
        // A, B는 투표하지 않은 채 15:00에 자동 마감되고, C·D의 표로 A가 지목된다. 투표한 C는 톡톡할 수 있다.
        Round round = announced(NOV_4, picks(memberC, memberA, memberD, memberA));
        assertThat(round.getResultType()).isEqualTo(RoundResultType.PICKED);

        tap(userC, round, 5).andExpect(status().isCreated());
    }

    @Test
    void RESULT_03_15_마감_직전에는_보낼_수_있다() throws Exception {
        Round round = pickedA();
        clock.set(DELIVERED.minusMinutes(1));

        tap(userB, round, 5).andExpect(status().isCreated());
    }

    @Test
    void RESULT_03_16_전달_전에는_빈_목록과_전달_시각이_나온다() throws Exception {
        Round round = pickedA();
        tap(userB, round, 5).andExpect(status().isCreated());
        clock.set(DELIVERED.minusMinutes(1));

        received(userA, round)
                .andExpect(jsonPath("$.data.isDelivered").value(false))
                .andExpect(jsonPath("$.data.deliveredAt").value("2026-11-05T20:00:00"))
                .andExpect(jsonPath("$.data.totalCount").value(0))
                .andExpect(jsonPath("$.data.taps").isEmpty());
    }

    @Test
    void 지목된_사람이_아니면_받은_톡톡을_볼_수_없다() throws Exception {
        Round round = pickedA();

        received(userB, round).andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("TAP_005"));
        played(userB, round).andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("TAP_005"));
    }

    @Test
    void RICE_01_7_톡톡_완료_보상() throws Exception {
        Round round = pickedA();

        tap(userB, round, 3).andExpect(jsonPath("$.data.riceEarned").value(5));
    }

    @Test
    void RICE_01_8_톡톡_건너뛰기는_보상_없음() throws Exception {
        Round round = pickedA();

        tap(userB, round, 0).andExpect(jsonPath("$.data.riceEarned").value(0));
    }

    // ---------- ROOM-06 결과 확인 저장 ----------

    @Test
    void ROOM_06_1_결과를_확인하면_안_본_결과가_비어_있다() throws Exception {
        Round round = pickedA();
        voteTab(userB).andExpect(jsonPath("$.data.status").value("RESULT_UNSEEN"));

        seenResult(userB, room, round.getId()).andExpect(status().isOk());

        voteTab(userB).andExpect(jsonPath("$.data.status").value("RESULT_SEEN"));
    }

    @Test
    void ROOM_06_2_이전_회차를_확인해도_저장값은_그대로다() throws Exception {
        Round earlier = announced(NOV_3, picks(memberB, memberA, memberC, memberA, memberD, memberA, memberA, memberB));
        Round later = announced(NOV_4, picks(memberB, memberA, memberC, memberA, memberD, memberA, memberA, memberB));
        seenResult(userB, room, later.getId()).andExpect(status().isOk());

        seenResult(userB, room, earlier.getId()).andExpect(status().isOk());

        assertThat(reload(memberB).getSeenResultRoundDate()).isEqualTo(NOV_4);
    }

    @Test
    void ROOM_06_3_다른_방의_회차면_거부하고_저장하지_않는다() throws Exception {
        Round round = pickedA();
        Room otherRoom = newRoom();
        addMember(otherRoom, userB, "지수", BosalColor.PURPLE);

        seenResult(userB, otherRoom, round.getId())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON_002"));

        assertThat(roomMemberRepository.findByRoom_IdAndUser_IdAndLeftAtIsNull(otherRoom.getId(), userB.getId())
                .orElseThrow().getSeenResultRoundDate()).isNull();
    }

    @Test
    void 결과_화면은_지목된_사람의_착용_아이템_코드를_돌려준다() throws Exception {
        wear(userA, "EQUIP_HAT", ItemSlot.HAT);
        Round round = announced(NOV_4, picks(memberB, memberA, memberC, memberA, memberD, null, memberA, memberB));

        result(userC, round).andExpect(jsonPath("$.data.picked.equippedItemCodes[0]").value("EQUIP_HAT"));
    }

    /** 착용한 아이템 하나를 준다. 시작 때 넣은 카탈로그와 겹치지 않는 코드를 쓴다. */
    private void wear(User user, String code, ItemSlot slot) {
        Item item = itemRepository.findByCode(code)
                .orElseGet(() -> itemRepository.save(Item.create(code, code, 100, slot, 99)));
        userItemRepository.save(UserItem.acquire(user, item));
    }

    // ---------- CLEAN-01 떠난 보살 기록 ----------

    @Test
    void CLEAN_01_6_정리_뒤에도_투표_결과는_그대로이고_떠난_보살로_표시된다() throws Exception {
        Round round = announced(NOV_4, picks(memberB, memberA, memberC, memberA, memberD, null, memberA, memberB));
        RoomMember left = reload(memberA);
        left.leave(AFTER_CLOSE, LeaveType.LEFT);
        roomMemberRepository.save(left);
        clock.set(AFTER_CLOSE.plusMonths(6).plusMinutes(1));

        leftMemberCleanupScheduler.clearExpiredRecords();

        result(userC, round)
                .andExpect(jsonPath("$.data.resultType").value("PICKED"))
                .andExpect(jsonPath("$.data.picked.memberId").value(memberA.getId()))
                .andExpect(jsonPath("$.data.picked.isLeft").value(true))
                .andExpect(jsonPath("$.data.picked.certificationResult").value("FAILED"))
                .andExpect(jsonPath("$.data.picked.spentAmount").isEmpty());
        assertThat(certificationRepository.findByRound_IdAndDeletedAtIsNull(round.getId()))
                .filteredOn(certification -> certification.getMember().getId().equals(memberA.getId()))
                .singleElement()
                .satisfies(certification -> assertThat(certification.getMemo()).isNull());
    }

    // ---------- 준비 ----------

    /** B·C·D가 A를, A가 B를 골라 A가 지목된 11/4 회차. */
    private Round pickedA() {
        return announced(NOV_4, picks(memberB, memberA, memberC, memberA, memberD, memberA, memberA, memberB));
    }

    /** 표: (투표자, 대상) 순서쌍. 대상이 null이면 "오늘은 모두 보살". */
    private static Map<RoomMember, RoomMember> picks(RoomMember... pairs) {
        Map<RoomMember, RoomMember> votes = new java.util.LinkedHashMap<>();
        for (int i = 0; i < pairs.length; i += 2) {
            votes.put(pairs[i], pairs[i + 1]);
        }
        return votes;
    }

    /** 그 날짜 회차를 인증 → 다음 날 03:00 투표 시작 → 표 → 다음 날 15:00 마감까지 진행한다. A는 못 참았어요(23,000원)로 인증한다. */
    private Round announced(LocalDate date, Map<RoomMember, RoomMember> votes) {
        Round round = startVoting(date);
        votes(round, votes);
        voteCloseService.closeAtDeadline(room.getId(), round.getId(), date.plusDays(1).atTime(15, 0));
        Round closed = reload(round);
        assertThat(closed.getStatus()).isEqualTo(RoundStatus.ANNOUNCED);
        return closed;
    }

    private Round startVoting(LocalDate date) {
        Round round = roundRepository.save(Round.create(room, date));
        for (RoomMember member : new RoomMember[] {memberA, memberB, memberC, memberD}) {
            boolean isA = member.getId().equals(memberA.getId());
            certificationRepository.save(Certification.create(round, member,
                    isA ? CertificationResult.FAILED : CertificationResult.ENDURED, "기록",
                    isA ? 23000 : null, date.atTime(20, 0)));
        }
        voteStartService.startAtDeadline(room.getId(), round.getId(), date.plusDays(1).atTime(3, 0));
        return reload(round);
    }

    private void votes(Round round, Map<RoomMember, RoomMember> votes) {
        votes.forEach((voter, target) -> voteRepository.save(Vote.create(reload(round), voter, target)));
    }

    private void defend(Round round) {
        Round loaded = reload(round);
        ReflectionTestUtils.setField(loaded, "resultType", RoundResultType.DEFENDED);
        roundRepository.save(loaded);
    }

    private ResultActions result(User user, Round round) throws Exception {
        return mockMvc.perform(get("/api/rounds/{roundId}/result", round.getId()).header("Authorization", bearer(user)));
    }

    private ResultActions tap(User user, Round round, int count) throws Exception {
        return mockMvc.perform(post("/api/rounds/{roundId}/taps", round.getId())
                .header("Authorization", bearer(user))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"count\": " + count + "}"));
    }

    private ResultActions received(User user, Round round) throws Exception {
        return mockMvc.perform(get("/api/rounds/{roundId}/taps/received", round.getId())
                .header("Authorization", bearer(user)));
    }

    private ResultActions played(User user, Round round) throws Exception {
        return mockMvc.perform(put("/api/rounds/{roundId}/taps/received/played", round.getId())
                .header("Authorization", bearer(user)));
    }

    private ResultActions seenResult(User user, Room target, Long roundId) throws Exception {
        return mockMvc.perform(put("/api/rooms/{roomId}/members/me/seen-result", target.getId())
                .header("Authorization", bearer(user))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"roundId\": " + roundId + "}"));
    }

    private ResultActions voteTab(User user) throws Exception {
        return mockMvc.perform(get("/api/rooms/{roomId}/vote", room.getId()).header("Authorization", bearer(user)));
    }

    private Tap tapOf(Round round, RoomMember member) {
        return tapRepository.findAll().stream()
                .filter(tap -> tap.getRound().getId().equals(round.getId())
                        && tap.getFromMember().getId().equals(member.getId()))
                .findFirst().orElseThrow();
    }

    private String bearer(User user) {
        return "Bearer " + jwtProvider.createAccessToken(user.getId());
    }

    private Round reload(Round round) {
        return roundRepository.findById(round.getId()).orElseThrow();
    }

    private RoomMember reload(RoomMember member) {
        return roomMemberRepository.findById(member.getId()).orElseThrow();
    }

    private Room newRoom() {
        return roomRepository.save(Room.create("배달 끊기 방", RoomGoal.DELIVERY, 4, 300));
    }

    private User newUser() {
        return userRepository.save(User.create("anon-result-" + userSequence++));
    }

    private RoomMember addMember(Room target, User user, String nickname, BosalColor color) {
        return addMember(target, user, nickname, color, NOV_2.minusDays(10).atStartOfDay());
    }

    private RoomMember addMember(Room target, User user, String nickname, BosalColor color, LocalDateTime joinedAt) {
        return roomMemberRepository.save(RoomMember.create(target, user, nickname, color, joinedAt));
    }
}
