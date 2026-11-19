package com.chamabosal.domain.pot.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.chamabosal.domain.certification.repository.CertificationRepository;
import com.chamabosal.domain.pot.entity.PotContribution;
import com.chamabosal.domain.pot.entity.PotReason;
import com.chamabosal.domain.pot.entity.RoomPot;
import com.chamabosal.domain.pot.repository.PotContributionRepository;
import com.chamabosal.domain.pot.repository.RoomPotRepository;
import com.chamabosal.domain.rice.entity.RiceHistory;
import com.chamabosal.domain.rice.entity.RiceReason;
import com.chamabosal.domain.rice.entity.RiceRefType;
import com.chamabosal.domain.rice.repository.RiceHistoryRepository;
import com.chamabosal.domain.room.entity.BosalColor;
import com.chamabosal.domain.room.entity.LeaveType;
import com.chamabosal.domain.room.entity.Room;
import com.chamabosal.domain.room.entity.RoomGoal;
import com.chamabosal.domain.room.entity.RoomMember;
import com.chamabosal.domain.room.repository.RoomMemberRepository;
import com.chamabosal.domain.room.repository.RoomRepository;
import com.chamabosal.domain.round.repository.RoundRepository;
import com.chamabosal.domain.store.entity.Item;
import com.chamabosal.domain.store.entity.ItemSlot;
import com.chamabosal.domain.store.entity.UserItem;
import com.chamabosal.domain.store.repository.ItemRepository;
import com.chamabosal.domain.store.repository.UserItemRepository;
import com.chamabosal.domain.user.entity.User;
import com.chamabosal.domain.user.repository.UserRepository;
import com.chamabosal.global.security.jwt.JwtProvider;
import com.chamabosal.support.MutableClock;
import com.jayway.jsonpath.JsonPath;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
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
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** 공양미 한 솥 (POT-01). 공통 Given: 목표 300석인 방에 A, B, C가 있고 인증 시간이다. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(MutableClock.Config.class)
class PotTest {

    private static final LocalDate DEC_1 = LocalDate.of(2026, 12, 1);
    private static final LocalDateTime T0 = DEC_1.atTime(20, 0);

    @Autowired MockMvc mockMvc;
    @Autowired MutableClock clock;
    @Autowired JwtProvider jwtProvider;
    @Autowired PlatformTransactionManager transactionManager;
    @Autowired RiceHistoryRepository riceHistoryRepository;
    @Autowired CertificationRepository certificationRepository;
    @Autowired RoundRepository roundRepository;
    @Autowired PotContributionRepository potContributionRepository;
    @Autowired RoomPotRepository roomPotRepository;
    @Autowired RoomMemberRepository roomMemberRepository;
    @Autowired RoomRepository roomRepository;
    @Autowired UserRepository userRepository;
    @Autowired ItemRepository itemRepository;
    @Autowired UserItemRepository userItemRepository;

    private int userSequence;
    private Room room;
    private User userA;
    private User userB;
    private User userC;
    private RoomMember memberA;
    private RoomMember memberB;
    private RoomMember memberC;

    @BeforeEach
    void setUp() {
        clock.set(T0);
        room = newRoom(300);
        userA = newUser();
        userB = newUser();
        userC = newUser();
        memberA = addMember(room, userA, "민지");
        memberB = addMember(room, userB, "지수");
        memberC = addMember(room, userC, "하린");
    }

    @AfterEach
    void tearDown() {
        userItemRepository.deleteAll();
        // 시작 때 넣은 카탈로그는 남기고 테스트가 만든 아이템만 지운다.
        itemRepository.findByCode("EQUIP_HAT").ifPresent(itemRepository::delete);
        riceHistoryRepository.deleteAll();
        certificationRepository.deleteAll();
        roundRepository.deleteAll();
        potContributionRepository.deleteAll();
        roomPotRepository.deleteAll();
        roomMemberRepository.deleteAll();
        roomRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    void POT_01_1_목표를_채우면_멤버_전원에게_보상하고_쌀가마_배지를_올린다() throws Exception {
        potAt(room, 295);

        certify(userA).andExpect(status().isCreated()).andExpect(jsonPath("$.data.potEarned").value(10));

        RoomPot pot = currentPot();
        assertThat(pot.getAmount()).isEqualTo(305);
        assertThat(pot.isCompleted()).isTrue();
        assertThat(potRewards()).extracting(RiceHistory::getDelta).containsExactly(30, 30, 30);
        assertThat(potRewards()).extracting(h -> h.getUser().getId())
                .containsExactlyInAnyOrder(userA.getId(), userB.getId(), userC.getId());
        assertThat(reloadRoom().getPotCompletedCount()).isEqualTo(1);
    }

    @Test
    void POT_01_2_동시에_목표를_넘겨도_달성과_보상은_한_번이다() throws Exception {
        potAt(room, 295);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            List<CompletableFuture<Integer>> results = List.of(userA, userB).stream()
                    .map(user -> CompletableFuture.supplyAsync(() -> {
                        await(start);
                        return statusOfCertify(user);
                    }, executor))
                    .toList();
            start.countDown();
            assertThat(results.stream().map(CompletableFuture::join)).containsExactly(201, 201);
        } finally {
            executor.shutdown();
        }

        assertThat(currentPot().getAmount()).isEqualTo(315);
        assertThat(potRewards()).hasSize(3);
        assertThat(reloadRoom().getPotCompletedCount()).isEqualTo(1);
    }

    @Test
    void POT_01_3_회수해도_솥은_0석_아래로_내려가지_않는다() throws Exception {
        Long certificationId = certificationIdOf(certify(userA).andExpect(status().isCreated()));
        setPotAmount(5);

        mockMvc.perform(delete("/api/certifications/{certificationId}", certificationId).header("Authorization", bearer(userA)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.potRecalled").value(5));

        assertThat(currentPot().getAmount()).isZero();
    }

    @Test
    void POT_01_4_축하를_본_뒤에는_다시_보여주지_않는다() throws Exception {
        potAt(room, 295);
        certify(userA).andExpect(status().isCreated());
        me(userA).andExpect(jsonPath("$.data.rooms[0].hasUnseen").value(true));

        mockMvc.perform(put("/api/rooms/{roomId}/members/me/seen-pot", room.getId())
                        .header("Authorization", bearer(userA))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"seenPotCount\": 1}"))
                .andExpect(status().isOk());

        me(userA).andExpect(jsonPath("$.data.rooms[0].hasUnseen").value(false));
    }

    @Test
    void POT_01_5_들어오기_전에_채운_솥은_축하하지_않는다() throws Exception {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            Room found = roomRepository.findById(room.getId()).orElseThrow();
            found.completePot();
            found.completePot();
        });
        User userE = newUser();

        addMember(reloadRoom(), userE, "은비");

        me(userE).andExpect(jsonPath("$.data.rooms[0].hasUnseen").value(false));
    }

    @Test
    void POT_01_6_넣은_석이_같으면_같은_순위이고_먼저_넣은_멤버가_앞이다() throws Exception {
        User userD = newUser();
        RoomMember memberD = addMember(room, userD, "도윤");
        RoomPot pot = completedPot(room, 300);
        contribute(pot, memberA, 120);
        contribute(pot, memberB, 90);
        contribute(pot, memberC, 90);
        contribute(pot, memberD, 200);
        leave(memberD);

        celebration(userA, 1)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.seq").value(1))
                .andExpect(jsonPath("$.data.goalAmount").value(300))
                .andExpect(jsonPath("$.data.rewardAmount").value(30))
                .andExpect(jsonPath("$.data.contributions.length()").value(3))
                .andExpect(jsonPath("$.data.contributions[*].memberId")
                        .value(org.hamcrest.Matchers.contains(
                                memberA.getId().intValue(), memberB.getId().intValue(), memberC.getId().intValue())))
                .andExpect(jsonPath("$.data.contributions[*].rank").value(org.hamcrest.Matchers.contains(1, 2, 2)))
                .andExpect(jsonPath("$.data.contributions[*].amount").value(org.hamcrest.Matchers.contains(120, 90, 90)));
    }

    @Test
    void 축하_화면은_멤버마다_착용_아이템_코드를_돌려준다() throws Exception {
        wear(userB, "EQUIP_HAT", ItemSlot.HAT);
        RoomPot pot = completedPot(room, 300);
        contribute(pot, memberA, 200);
        contribute(pot, memberB, 100);

        celebration(userA, 1)
                .andExpect(jsonPath("$.data.contributions[0].equippedItemCodes.length()").value(0))
                .andExpect(jsonPath("$.data.contributions[1].equippedItemCodes[0]").value("EQUIP_HAT"));
    }

    /** 착용한 아이템 하나를 준다. 시작 때 넣은 카탈로그와 겹치지 않는 코드를 쓴다. */
    private void wear(User user, String code, ItemSlot slot) {
        Item item = itemRepository.findByCode(code)
                .orElseGet(() -> itemRepository.save(Item.create(code, code, 100, slot, 99)));
        userItemRepository.save(UserItem.acquire(user, item));
    }

    @Test
    void POT_01_7_다_채우지_않은_솥은_POT_001() throws Exception {
        potAt(room, 100);

        celebration(userA, 1).andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("POT_001"));
        celebration(userA, 2).andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("POT_001"));
    }

    @Test
    void POT_01_8_보너스는_목표의_10퍼센트다() throws Exception {
        Room room500 = newRoom(500);
        addMember(room500, userA, "민지");
        addMember(room500, userB, "지수");
        potAt(room500, 495);

        certifyIn(userA, room500).andExpect(status().isCreated());

        assertThat(potRewards()).extracting(RiceHistory::getDelta).containsExactly(50, 50);
    }

    /** RICE-01 8번: 10%가 10석 단위로 떨어지지 않으면 반올림한다 (Q-81). */
    @Test
    void 보너스는_10석_단위로_반올림한다() throws Exception {
        Room room350 = newRoom(350);
        addMember(room350, userA, "민지");
        addMember(room350, userB, "지수");
        potAt(room350, 345);

        certifyIn(userA, room350).andExpect(status().isCreated());

        assertThat(potRewards()).extracting(RiceHistory::getDelta).containsExactly(40, 40);
    }

    private ResultActions certify(User user) throws Exception {
        return certifyIn(user, room);
    }

    private ResultActions certifyIn(User user, Room target) throws Exception {
        return mockMvc.perform(post("/api/rooms/{roomId}/certifications", target.getId())
                .header("Authorization", bearer(user))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"result": "ENDURED", "memo": "집밥 해먹었어요"}
                        """));
    }

    private int statusOfCertify(User user) {
        try {
            return certify(user).andReturn().getResponse().getStatus();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static Long certificationIdOf(ResultActions result) throws Exception {
        String content = result.andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(content, "$.data.certificationId")).longValue();
    }

    private ResultActions celebration(User user, int seq) throws Exception {
        return mockMvc.perform(get("/api/rooms/{roomId}/pots/{seq}", room.getId(), seq).header("Authorization", bearer(user)));
    }

    private ResultActions me(User user) throws Exception {
        return mockMvc.perform(get("/api/users/me").header("Authorization", bearer(user))).andExpect(status().isOk());
    }

    private void potAt(Room target, int amount) {
        RoomPot pot = RoomPot.first(target);
        pot.add(amount);
        roomPotRepository.save(pot);
    }

    private RoomPot completedPot(Room target, int amount) {
        RoomPot pot = RoomPot.first(target);
        pot.add(amount);
        pot.complete(T0);
        return roomPotRepository.save(pot);
    }

    private void contribute(RoomPot pot, RoomMember member, int amount) {
        potContributionRepository.save(PotContribution.create(pot, member, amount, PotReason.CERTIFICATION, (long) amount));
    }

    private void setPotAmount(int amount) {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            RoomPot pot = roomPotRepository.findFirstByRoom_IdOrderBySeqDesc(room.getId()).orElseThrow();
            pot.subtract(pot.getAmount() - amount);
        });
    }

    private void leave(RoomMember member) {
        new TransactionTemplate(transactionManager).executeWithoutResult(status ->
                roomMemberRepository.findById(member.getId()).orElseThrow().leave(T0, LeaveType.LEFT));
    }

    private RoomPot currentPot() {
        return roomPotRepository.findFirstByRoom_IdOrderBySeqDesc(room.getId()).orElseThrow();
    }

    private List<RiceHistory> potRewards() {
        return riceHistoryRepository.findAll().stream()
                .filter(h -> h.getReason() == RiceReason.POT && h.getRefType() == RiceRefType.ROOM_POT)
                .toList();
    }

    private Room reloadRoom() {
        return roomRepository.findById(room.getId()).orElseThrow();
    }

    private String bearer(User user) {
        return "Bearer " + jwtProvider.createAccessToken(user.getId());
    }

    private static void await(CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private Room newRoom(int goal) {
        return roomRepository.save(Room.create("배달 끊기 방", RoomGoal.DELIVERY, 4, goal));
    }

    private User newUser() {
        return userRepository.save(User.create("anon-pot-" + userSequence++));
    }

    private RoomMember addMember(Room target, User user, String nickname) {
        return roomMemberRepository.save(RoomMember.create(target, user, nickname, BosalColor.PINK, DEC_1.minusDays(10).atStartOfDay()));
    }
}
