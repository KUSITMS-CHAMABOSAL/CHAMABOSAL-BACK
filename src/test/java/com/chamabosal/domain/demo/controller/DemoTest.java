package com.chamabosal.domain.demo.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.chamabosal.domain.certification.entity.Certification;
import com.chamabosal.domain.certification.entity.CertificationResult;
import com.chamabosal.domain.certification.repository.CertificationRepository;
import com.chamabosal.domain.defense.entity.DefenseTicket;
import com.chamabosal.domain.defense.repository.DefenseTicketRepository;
import com.chamabosal.domain.demo.dto.response.DemoResetResponse;
import com.chamabosal.domain.demo.service.DemoResetService;
import com.chamabosal.domain.demo.service.DemoSettings;
import com.chamabosal.domain.demo.service.DemoSettings.Slot;
import com.chamabosal.domain.nabal.entity.NabalLoss;
import com.chamabosal.domain.nabal.repository.NabalLossRepository;
import com.chamabosal.domain.pot.repository.PotContributionRepository;
import com.chamabosal.domain.pot.repository.RoomPotRepository;
import com.chamabosal.domain.rice.entity.RiceHistory;
import com.chamabosal.domain.rice.entity.RiceReason;
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
import com.chamabosal.domain.store.entity.Item;
import com.chamabosal.domain.store.entity.UserItem;
import com.chamabosal.domain.store.repository.ItemRepository;
import com.chamabosal.domain.store.repository.UserItemRepository;
import com.chamabosal.domain.tap.repository.TapRepository;
import com.chamabosal.domain.tap.repository.TapStartRepository;
import com.chamabosal.domain.user.entity.User;
import com.chamabosal.domain.user.repository.UserRepository;
import com.chamabosal.domain.vote.entity.Vote;
import com.chamabosal.domain.vote.repository.VoteRepository;
import com.chamabosal.domain.vote.service.VoteStartService;
import com.chamabosal.global.security.jwt.JwtProvider;
import com.chamabosal.support.MutableClock;
import com.jayway.jsonpath.JsonPath;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
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
 * 전시 시연 (DEMO-01 시연 리셋, DEMO-02 가짜 팀원 자동 투표, DEMO-03 톡톡 뒤 나발 빠짐).
 * 공통 Given: 시연 설정이 켜져 있고 시연 번호 1, 2에 시연 계정이 하나씩 있다. 지금은 11/28 13:00이다.
 */
@SpringBootTest(properties = {
        "demo.enabled=true",
        "demo.key=" + DemoTest.DEMO_KEY
})
@AutoConfigureMockMvc
@Import(MutableClock.Config.class)
class DemoTest {

    static final String DEMO_KEY = "demo-test-key-0123456789abcdefghijklmnop";
    private static final LocalDate NOV_27 = LocalDate.of(2026, 11, 27);
    private static final LocalDate NOV_28 = LocalDate.of(2026, 11, 28);
    private static final LocalDateTime NOW = NOV_28.atTime(13, 0);

    @Autowired
    MockMvc mockMvc;

    @Autowired
    MutableClock clock;

    @Autowired
    JwtProvider jwtProvider;

    @Autowired
    DemoSettings demoSettings;

    @Autowired
    DemoResetService demoResetService;

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

    @Autowired
    VoteRepository voteRepository;

    @Autowired
    TapRepository tapRepository;

    @Autowired
    TapStartRepository tapStartRepository;

    @Autowired
    RiceHistoryRepository riceHistoryRepository;

    @Autowired
    PotContributionRepository potContributionRepository;

    @Autowired
    RoomPotRepository roomPotRepository;

    @Autowired
    NabalLossRepository nabalLossRepository;

    @Autowired
    ItemRepository itemRepository;

    @Autowired
    UserItemRepository userItemRepository;

    @Autowired
    DefenseTicketRepository defenseTicketRepository;

    private User demo1;
    private User demo2;

    @BeforeEach
    void setUp() {
        clock.set(NOW);
        demo1 = userRepository.save(User.create("anon-demo-1"));
        demo2 = userRepository.save(User.create("anon-demo-2"));
        ReflectionTestUtils.setField(demoSettings, "slots", Map.of(
                1, new Slot(1, demo1.getId(), "참는보살"),
                2, new Slot(2, demo2.getId(), "절약왕")));
    }

    @AfterEach
    void tearDown() {
        ReflectionTestUtils.setField(demoSettings, "enabled", true);
        ReflectionTestUtils.setField(demoSettings, "slots", Map.of());
        userItemRepository.deleteAll();
        defenseTicketRepository.deleteAll();
        nabalLossRepository.deleteAll();
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

    // ---------- DEMO-01 시연 리셋 ----------

    @Test
    void DEMO_01_1_시연_설정이_꺼져_있으면_없는_API로_거부한다() throws Exception {
        ReflectionTestUtils.setField(demoSettings, "enabled", false);

        reset(1).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("COMMON_001"));
    }

    @Test
    void DEMO_01_2_시연_키가_틀리면_거부한다() throws Exception {
        mockMvc.perform(post("/api/demo/reset").param("slot", "1").header("X-Demo-Key", "wrong-key"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("COMMON_008"));
    }

    @Test
    void DEMO_01_3_설정에_없는_시연_번호는_거부한다() throws Exception {
        reset(9).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("DEMO_001"));

        ReflectionTestUtils.setField(demoSettings, "slots", Map.of(3, new Slot(3, Long.MAX_VALUE, "없는계정")));
        reset(3).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("DEMO_001"));
    }

    @Test
    void DEMO_01_4_첫_리셋이면_가짜_팀원과_시연_방_2개를_만든다() throws Exception {
        DemoRooms rooms = resetOk(1);

        assertThat(fakeUsers(1)).hasSize(3);
        Room main = roomRepository.findById(rooms.main()).orElseThrow();
        assertThat(main.getName()).isEqualTo("자율 절약 방");
        assertThat(main.getGoal()).isEqualTo(RoomGoal.ETC);
        assertThat(main.getPotGoalAmount()).isEqualTo(300);
        Room moktak = roomRepository.findById(rooms.moktak()).orElseThrow();
        assertThat(moktak.getName()).isEqualTo("배달 끊기 방");
        assertThat(moktak.getGoal()).isEqualTo(RoomGoal.DELIVERY);
        assertThat(moktak.getPotGoalAmount()).isEqualTo(300);
        assertThat(roomMemberRepository.findByRoom_IdAndLeftAtIsNull(rooms.main())).hasSize(4);
        assertThat(roomMemberRepository.findByRoom_IdAndLeftAtIsNull(rooms.moktak())).extracting(RoomMember::getNickname)
                .containsExactlyInAnyOrder("참는보살", "예나", "예은", "윤지");

        Round round = roundRepository.findByRoom_IdAndRoundDate(rooms.main(), NOV_28).orElseThrow();
        assertThat(round.getStatus()).isEqualTo(RoundStatus.CERTIFYING);
        Map<Long, String> nicknames = nicknames();
        List<Certification> certifications = certificationRepository.findAll().stream()
                .filter(certification -> certification.getRound().getId().equals(round.getId()))
                .toList();
        assertThat(certifications).extracting(certification -> nicknames.get(certification.getMember().getId()))
                .containsExactlyInAnyOrder("민서", "만재", "용선");
        Certification hyunbin = certifications.stream()
                .filter(certification -> nicknames.get(certification.getMember().getId()).equals("용선"))
                .findFirst().orElseThrow();
        assertThat(hyunbin.getResult()).isEqualTo(CertificationResult.FAILED);
        assertThat(hyunbin.getMemo()).isEqualTo("세일 문자 보고 결국 못 참고 질러버렸어요");
        assertThat(hyunbin.getSpentAmount()).isEqualTo(49_210);

        assertThat(riceHistoryRepository.sumDeltaByUserId(demo1.getId())).isEqualTo(200);
    }

    @Test
    void DEMO_01_5_시연_방_2는_받은_톡톡_10회가_전달돼_목탁을_할_수_있다() throws Exception {
        DemoRooms rooms = resetOk(1);
        Round round = roundRepository.findByRoom_IdAndRoundDate(rooms.moktak(), NOV_27).orElseThrow();

        mockMvc.perform(get("/api/rounds/{roundId}/taps/received", round.getId()).header("Authorization", bearer(demo1)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.isDelivered").value(true))
                .andExpect(jsonPath("$.data.totalCount").value(10))
                .andExpect(jsonPath("$.data.isMoktakDone").value(false));
        mockMvc.perform(get("/api/rooms/{roomId}/vote", rooms.moktak()).header("Authorization", bearer(demo1)))
                .andExpect(jsonPath("$.data.status").value("MOKTAK"));
    }

    @Test
    void DEMO_01_6_다시_리셋하면_이전_방을_지우고_시연_계정을_처음_상태로_되돌린다() throws Exception {
        DemoRooms before = resetOk(1);
        Round round = visitorPicksC(before.main());
        tap(demo1, round, 3).andExpect(status().isCreated());
        Item item = itemRepository.findAll().get(0);
        userItemRepository.save(UserItem.acquire(demo1, item));
        riceHistoryRepository.save(RiceHistory.create(demo1, null, -50, RiceReason.PURCHASE, null, null));
        defenseTicketRepository.save(DefenseTicket.purchase(demo1, NOW));

        DemoRooms after = resetOk(1);

        assertThat(roomRepository.findById(before.main()).orElseThrow().isDeleted()).isTrue();
        assertThat(roomRepository.findById(before.moktak()).orElseThrow().isDeleted()).isTrue();
        Long oldRoundId = round.getId();
        assertThat(certificationRepository.findAll().stream()
                .filter(certification -> certification.getRound().getId().equals(oldRoundId)))
                .isNotEmpty()
                .allSatisfy(certification -> {
                    assertThat(certification.getMemo()).isNull();
                    assertThat(certification.getSpentAmount()).isNull();
                });
        assertThat(roomRepository.findById(after.main()).orElseThrow().isDeleted()).isFalse();
        assertThat(after.main()).isNotEqualTo(before.main());
        assertThat(fakeUsers(1)).hasSize(3);
        assertThat(riceHistoryRepository.sumDeltaByUserId(demo1.getId())).isEqualTo(200);
        assertThat(nabalLossRepository.findByUser_IdAndReplantedAtIsNullOrderByNabalNumber(demo1.getId())).isEmpty();
        assertThat(userItemRepository.findWithItemByUserId(demo1.getId())).isEmpty();
        assertThat(defenseTicketRepository.countByUser_IdAndUsedAtIsNull(demo1.getId())).isZero();
    }

    @Test
    void DEMO_01_7_시연_계정이_일반_방에_있으면_거부하고_아무것도_바꾸지_않는다() throws Exception {
        Room regular = roomRepository.save(Room.create("일반 방", RoomGoal.SNACK, 4, 300));
        User friend = userRepository.save(User.create("anon-demo-friend"));
        addMember(regular, demo1, "나", BosalColor.PINK);
        addMember(regular, friend, "친구", BosalColor.MINT);

        reset(1).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DEMO_002"));

        assertThat(roomMemberRepository.findCurrentWithRoom(demo1.getId())).hasSize(1);
        assertThat(fakeUsers(1)).isEmpty();
        assertThat(riceHistoryRepository.sumDeltaByUserId(demo1.getId())).isZero();
    }

    @Test
    void DEMO_01_8_다른_시연_번호의_방과_공양미는_그대로다() throws Exception {
        resetOk(1);
        DemoRooms slot2 = resetOk(2);
        riceHistoryRepository.save(RiceHistory.create(demo2, null, 50, RiceReason.CERTIFICATION, null, null));

        resetOk(1);

        assertThat(roomRepository.findById(slot2.main()).orElseThrow().isDeleted()).isFalse();
        assertThat(roomRepository.findById(slot2.moktak()).orElseThrow().isDeleted()).isFalse();
        assertThat(roomMemberRepository.findCurrentWithRoom(demo2.getId())).hasSize(2);
        assertThat(riceHistoryRepository.sumDeltaByUserId(demo2.getId())).isEqualTo(250);
    }

    @Test
    void DEMO_01_9_같은_시연_번호를_동시에_리셋해도_방은_2개다() throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(1);
        try {
            List<Future<DemoResetResponse>> results = List.of(
                    executor.submit(() -> {
                        ready.await();
                        return demoResetService.reset(DEMO_KEY, 1);
                    }),
                    executor.submit(() -> {
                        ready.await();
                        return demoResetService.reset(DEMO_KEY, 1);
                    }));
            ready.countDown();
            for (Future<DemoResetResponse> result : results) {
                assertThat(result.get(30, TimeUnit.SECONDS)).isNotNull();
            }
        } finally {
            executor.shutdownNow();
        }

        assertThat(roomMemberRepository.findCurrentWithRoom(demo1.getId())).hasSize(2);
        assertThat(fakeUsers(1)).hasSize(3);
        assertThat(riceHistoryRepository.sumDeltaByUserId(demo1.getId())).isEqualTo(200);
    }

    @Test
    void DEMO_01_10_가짜_팀원을_모두_내보낸_시연_방도_리셋한다() throws Exception {
        DemoRooms before = resetOk(1);
        for (String nickname : List.of("민서", "만재", "용선")) {
            mockMvc.perform(delete("/api/rooms/{roomId}/members/{memberId}", before.main(),
                            member(before.main(), nickname).getId())
                            .header("Authorization", bearer(demo1)))
                    .andExpect(status().is2xxSuccessful());
        }
        assertThat(roomMemberRepository.findByRoom_IdAndLeftAtIsNull(before.main())).hasSize(1);

        DemoRooms after = resetOk(1);

        assertThat(roomRepository.findById(before.main()).orElseThrow().isDeleted()).isTrue();
        assertThat(roomMemberRepository.findCurrentWithRoom(demo1.getId())).extracting(m -> m.getRoom().getId())
                .containsExactlyInAnyOrder(after.main(), after.moktak());
    }

    // ---------- DEMO-02 가짜 팀원 자동 투표 ----------

    @Test
    void DEMO_02_1_방문객이_인증하면_투표가_시작되고_가짜_팀원_표가_들어간다() throws Exception {
        DemoRooms rooms = resetOk(1);

        certify(demo1, rooms.main()).andExpect(status().isCreated());

        Round round = roundRepository.findByRoom_IdAndRoundDate(rooms.main(), NOV_28).orElseThrow();
        assertThat(round.getStatus()).isEqualTo(RoundStatus.VOTING);
        Map<String, String> votes = votesOf(round);
        assertThat(votes).containsExactlyInAnyOrderEntriesOf(Map.of(
                "민서", "용선",
                "만재", "용선",
                "용선", "ALL_BOSAL"));
        for (User fake : fakeUsers(1)) {
            assertThat(riceHistoryRepository.sumDeltaByUserId(fake.getId())).isZero();
        }
    }

    @Test
    void DEMO_02_2_방문객이_누구에게_투표해도_C가_지목된다() throws Exception {
        DemoRooms rooms = resetOk(1);
        certify(demo1, rooms.main()).andExpect(status().isCreated());
        Round round = roundRepository.findByRoom_IdAndRoundDate(rooms.main(), NOV_28).orElseThrow();

        vote(demo1, round, member(rooms.main(), "민서")).andExpect(status().isCreated());

        Round closed = roundRepository.findById(round.getId()).orElseThrow();
        assertThat(closed.getStatus()).isEqualTo(RoundStatus.ANNOUNCED);
        assertThat(closed.getResultType()).isEqualTo(RoundResultType.PICKED);
        assertThat(roomMemberRepository.findById(closed.getPickedMember().getId()).orElseThrow().getNickname())
                .isEqualTo("용선");
    }

    @Test
    void DEMO_02_3_일반_방은_투표가_시작돼도_자동_표가_없다() throws Exception {
        Room regular = roomRepository.save(Room.create("일반 방", RoomGoal.SNACK, 4, 300));
        User a = userRepository.save(User.create("anon-demo-a"));
        User b = userRepository.save(User.create("anon-demo-b"));
        User c = userRepository.save(User.create("anon-demo-c"));
        addMember(regular, a, "가", BosalColor.PINK);
        addMember(regular, b, "나", BosalColor.MINT);
        addMember(regular, c, "다", BosalColor.PURPLE);

        for (User user : List.of(a, b, c)) {
            certify(user, regular.getId()).andExpect(status().isCreated());
        }

        Round round = roundRepository.findByRoom_IdAndRoundDate(regular.getId(), NOV_28).orElseThrow();
        assertThat(round.getStatus()).isEqualTo(RoundStatus.VOTING);
        assertThat(voteRepository.countByRound_Id(round.getId())).isZero();
    }

    @Test
    void DEMO_02_4_방문객이_인증하지_않은_채_03시가_되면_자동_표가_없다() throws Exception {
        DemoRooms rooms = resetOk(1);
        Round round = roundRepository.findByRoom_IdAndRoundDate(rooms.main(), NOV_28).orElseThrow();

        voteStartService.startAtDeadline(rooms.main(), round.getId(), NOV_28.plusDays(1).atTime(3, 0));

        assertThat(roundRepository.findById(round.getId()).orElseThrow().getStatus()).isEqualTo(RoundStatus.VOTING);
        assertThat(voteRepository.countByRound_Id(round.getId())).isZero();
    }

    // ---------- DEMO-03 톡톡 뒤 나발 빠짐 ----------

    @Test
    void DEMO_03_1_시연_계정이_톡톡을_보내면_나발이_1개_빠진다() throws Exception {
        DemoRooms rooms = resetOk(1);
        Round round = visitorPicksC(rooms.main());

        tap(demo1, round, 5).andExpect(status().isCreated());

        List<NabalLoss> losses = nabalLossRepository.findByUser_IdAndReplantedAtIsNullOrderByNabalNumber(demo1.getId());
        assertThat(losses).hasSize(1);
        mockMvc.perform(get("/api/users/me").header("Authorization", bearer(demo1)))
                .andExpect(jsonPath("$.data.nabal.unseenFallenNumbers[0]").value(losses.get(0).getNabalNumber()));
    }

    @Test
    void DEMO_03_2_건너뛰기여도_나발이_빠진다() throws Exception {
        DemoRooms rooms = resetOk(1);
        Round round = visitorPicksC(rooms.main());

        tap(demo1, round, 0).andExpect(status().isCreated());

        assertThat(nabalLossRepository.findByUser_IdAndReplantedAtIsNullOrderByNabalNumber(demo1.getId())).hasSize(1);
    }

    @Test
    void DEMO_03_3_일반_방에서_톡톡을_보내면_나발은_그대로다() throws Exception {
        Room regular = roomRepository.save(Room.create("일반 방", RoomGoal.SNACK, 4, 300));
        User b = userRepository.save(User.create("anon-demo-b"));
        User c = userRepository.save(User.create("anon-demo-c"));
        RoomMember me = addMember(regular, demo1, "나", BosalColor.PINK);
        RoomMember memberB = addMember(regular, b, "비", BosalColor.MINT);
        RoomMember memberC = addMember(regular, c, "씨", BosalColor.PURPLE);
        for (User user : List.of(demo1, b, c)) {
            certify(user, regular.getId()).andExpect(status().isCreated());
        }
        Round round = roundRepository.findByRoom_IdAndRoundDate(regular.getId(), NOV_28).orElseThrow();
        vote(demo1, round, memberB).andExpect(status().isCreated());
        vote(c, round, memberB).andExpect(status().isCreated());
        vote(b, round, memberC).andExpect(status().isCreated());
        assertThat(roundRepository.findById(round.getId()).orElseThrow().getPickedMember().getId())
                .isEqualTo(memberB.getId());

        tap(demo1, round, 5).andExpect(status().isCreated());

        assertThat(nabalLossRepository.findByUser_IdAndReplantedAtIsNullOrderByNabalNumber(me.getUser().getId()))
                .isEmpty();
    }

    // ---------- helpers ----------

    private record DemoRooms(Long main, Long moktak) {
    }

    /** 방문객이 인증하고 민서에게 투표해 C(용선)가 지목된 시연 방 1 회차. */
    private Round visitorPicksC(Long mainRoomId) throws Exception {
        certify(demo1, mainRoomId).andExpect(status().isCreated());
        Round round = roundRepository.findByRoom_IdAndRoundDate(mainRoomId, NOV_28).orElseThrow();
        vote(demo1, round, member(mainRoomId, "민서")).andExpect(status().isCreated());
        return round;
    }

    private DemoRooms resetOk(int slot) throws Exception {
        String body = reset(slot).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return new DemoRooms(
                ((Number) JsonPath.read(body, "$.data.mainRoomId")).longValue(),
                ((Number) JsonPath.read(body, "$.data.moktakRoomId")).longValue());
    }

    private ResultActions reset(int slot) throws Exception {
        return mockMvc.perform(post("/api/demo/reset").param("slot", String.valueOf(slot)).header("X-Demo-Key", DEMO_KEY));
    }

    private ResultActions certify(User user, Long roomId) throws Exception {
        return mockMvc.perform(post("/api/rooms/{roomId}/certifications", roomId)
                .header("Authorization", bearer(user))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"result\": \"ENDURED\", \"memo\": \"오늘은 참았어요\"}"));
    }

    private ResultActions vote(User user, Round round, RoomMember target) throws Exception {
        return mockMvc.perform(post("/api/rounds/{roundId}/votes", round.getId())
                .header("Authorization", bearer(user))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"targetType\": \"MEMBER\", \"targetMemberId\": " + target.getId() + "}"));
    }

    private ResultActions tap(User user, Round round, int count) throws Exception {
        return mockMvc.perform(post("/api/rounds/{roundId}/taps", round.getId())
                .header("Authorization", bearer(user))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"count\": " + count + "}"));
    }

    /** 투표자 닉네임 → 대상 닉네임 (모두 보살이면 ALL_BOSAL). */
    private Map<String, String> votesOf(Round round) {
        Map<Long, String> nicknames = nicknames();
        Map<String, String> votes = new java.util.HashMap<>();
        for (Vote vote : voteRepository.findByRound_Id(round.getId())) {
            String target = vote.getTargetMember() == null ? "ALL_BOSAL" : nicknames.get(vote.getTargetMember().getId());
            votes.put(nicknames.get(vote.getVoterMember().getId()), target);
        }
        return votes;
    }

    private Map<Long, String> nicknames() {
        Map<Long, String> nicknames = new java.util.HashMap<>();
        roomMemberRepository.findAll().forEach(member -> nicknames.put(member.getId(), member.getNickname()));
        return nicknames;
    }

    private RoomMember member(Long roomId, String nickname) {
        return roomMemberRepository.findByRoom_IdAndLeftAtIsNull(roomId).stream()
                .filter(member -> member.getNickname().equals(nickname))
                .findFirst().orElseThrow();
    }

    private List<User> fakeUsers(int slot) {
        return userRepository.findAll().stream()
                .filter(user -> user.getTossAnonKey() != null
                        && user.getTossAnonKey().startsWith(DemoSettings.FAKE_KEY_PREFIX + slot + "-"))
                .toList();
    }

    private RoomMember addMember(Room room, User user, String nickname, BosalColor color) {
        return roomMemberRepository.save(RoomMember.create(room, user, nickname, color, NOV_27.atTime(9, 0)));
    }

    private String bearer(User user) {
        return "Bearer " + jwtProvider.createAccessToken(user.getId());
    }
}
