package com.chamabosal.domain.rice.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.chamabosal.domain.pot.repository.PotContributionRepository;
import com.chamabosal.domain.pot.repository.RoomPotRepository;
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
import com.chamabosal.domain.store.entity.Item;
import com.chamabosal.domain.store.entity.ItemSlot;
import com.chamabosal.domain.store.entity.UserItem;
import com.chamabosal.domain.store.repository.ItemRepository;
import com.chamabosal.domain.store.repository.UserItemRepository;
import com.chamabosal.domain.user.entity.User;
import com.chamabosal.domain.user.repository.UserRepository;
import com.chamabosal.global.security.jwt.JwtProvider;
import com.chamabosal.support.MutableClock;
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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 방 나가기 차감 (RICE-03). 공통 Given: 방 1에 A, B가 있다. 적립·회수는 장부에 직접 기록한다.
 * 한 솥 완성 횟수를 따로 올리지 않으면 방은 목표를 채우기 전이다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(MutableClock.Config.class)
class RiceLeaveDeductTest {

    private static final LocalDate DEC_1 = LocalDate.of(2026, 12, 1);
    private static final LocalDateTime T0 = DEC_1.atTime(20, 0);

    @Autowired
    MockMvc mockMvc;

    @Autowired
    MutableClock clock;

    @Autowired
    JwtProvider jwtProvider;

    @Autowired
    PlatformTransactionManager transactionManager;

    @Autowired
    RiceHistoryRepository riceHistoryRepository;

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
    ItemRepository itemRepository;

    @Autowired
    UserItemRepository userItemRepository;

    private int userSequence;
    private long refSequence = 1;
    private Room room1;
    private Room room2;
    private User userA;
    private RoomMember memberA;
    private User ownerB;

    @BeforeEach
    void setUp() {
        clock.set(T0);
        room1 = newRoom();
        room2 = newRoom();
        userA = newUser();
        memberA = addMember(room1, userA, "민지");
        ownerB = newUser();
        RoomMember memberB = addMember(room1, ownerB, "지수");
        room1.assignOwner(memberB.getId());
        room1 = roomRepository.save(room1);
        addMember(room2, userA, "민지");
        addMember(room2, newUser(), "하린");
    }

    @AfterEach
    void tearDown() {
        userItemRepository.deleteAll();
        itemRepository.findByCode("LEAVE_HAT").ifPresent(itemRepository::delete);
        riceHistoryRepository.deleteAll();
        potContributionRepository.deleteAll();
        roomPotRepository.deleteAll();
        roomMemberRepository.deleteAll();
        roomRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    void RICE_03_1_나가면_그_방에서_모은_공양미를_차감한다() throws Exception {
        earn(room1, 200);
        earn(room2, 150);

        leave(room1).andExpect(status().isOk());

        assertThat(leaveDeltas()).containsExactly(-200);
        assertThat(balance()).isEqualTo(150);
    }

    @Test
    void RICE_03_2_차감_뒤_음수가_되면_0석까지만_차감한다() throws Exception {
        earn(room1, 200);
        purchase(80);

        leave(room1).andExpect(status().isOk());

        assertThat(leaveDeltas()).containsExactly(-120);
        assertThat(balance()).isZero();
    }

    @Test
    void RICE_03_3_회수는_빼고_차감한다() throws Exception {
        Long first = earn(room1, 10);
        earn(room1, 10);
        earn(room1, 10);
        recall(room1, first, 10);
        earn(room2, 100);

        leave(room1).andExpect(status().isOk());

        assertThat(leaveDeltas()).containsExactly(-20);
    }

    @Test
    void RICE_03_4_다른_방에서_모은_공양미는_그대로다() throws Exception {
        earn(room1, 100);
        earn(room2, 50);

        leave(room1).andExpect(status().isOk());

        assertThat(leaveDeltas()).containsExactly(-100);
        assertThat(balance()).isEqualTo(50);
    }

    @Test
    void RICE_03_6_목표를_채운_뒤_나가면_차감하지_않는다() throws Exception {
        earn(room1, 200);
        new TransactionTemplate(transactionManager).executeWithoutResult(status ->
                roomRepository.findById(room1.getId()).orElseThrow().completePot());

        leave(room1).andExpect(status().isOk());

        assertThat(leaveDeltas()).isEmpty();
        assertThat(balance()).isEqualTo(200);
    }

    @Test
    void RICE_03_7_다시_들어온_뒤_또_나가면_다시_들어온_뒤_모은_것만_차감한다() throws Exception {
        earn(room1, 100);
        earn(room2, 200);
        leave(room1).andExpect(status().isOk());

        clock.set(T0.plusHours(1));
        rejoin(memberA);
        earn(room1, 10);

        leave(room1).andExpect(status().isOk());

        assertThat(leaveDeltas()).containsExactly(-100, -10);
        assertThat(balance()).isEqualTo(200);
    }

    @Test
    void RICE_03_8_이미_잔액이_음수면_차감하지_않는다() throws Exception {
        earn(room1, 10);
        purchase(20);
        assertThat(balance()).isEqualTo(-10);

        leave(room1).andExpect(status().isOk());

        assertThat(leaveDeltas()).isEmpty();
        assertThat(balance()).isEqualTo(-10);
    }

    @Test
    void 같은_나가기를_다시_요청해도_차감은_한_번이다() throws Exception {
        earn(room1, 100);
        earn(room2, 100);
        leave(room1).andExpect(status().isOk());

        leave(room1)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ROOM_002"));

        assertThat(leaveDeltas()).containsExactly(-100);
    }

    /** 0석을 빼서 차감 기록이 없어도, 다시 들어온 뒤 또 나가면 다시 들어온 뒤 모은 것만 뺀다 (코드리뷰 반영). */
    @Test
    void RICE_03_9_0석을_빼고_나간_뒤_다시_들어와_또_나가면_다시_들어온_뒤_모은_것만_차감한다() throws Exception {
        earn(room1, 100);
        purchase(100);
        leave(room1).andExpect(status().isOk());
        assertThat(leaveDeltas()).isEmpty();

        clock.set(T0.plusHours(1));
        rejoin(memberA);
        earn(room1, 10);
        earn(room2, 100);

        leave(room1).andExpect(status().isOk());

        assertThat(leaveDeltas()).containsExactly(-10);
        assertThat(balance()).isEqualTo(100);
    }

    /** 두 방은 서로 다른 방 행 락을 쓴다. 사용자 행 락이 없으면 둘 다 잔액 120석을 보고 100석씩 빼 -80석이 된다 (코드리뷰 반영). */
    @Test
    void RICE_03_10_같은_사람이_두_방을_동시에_나가도_잔액은_0석_아래로_내려가지_않는다() {
        earn(room1, 100);
        earn(room2, 100);
        purchase(80);

        CountDownLatch ready = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            CompletableFuture<Integer> leave1 = CompletableFuture.supplyAsync(() -> {
                await(ready);
                return statusOf(room1);
            }, executor);
            CompletableFuture<Integer> leave2 = CompletableFuture.supplyAsync(() -> {
                await(ready);
                return statusOf(room2);
            }, executor);
            ready.countDown();
            assertThat(leave1.join()).isEqualTo(200);
            assertThat(leave2.join()).isEqualTo(200);
        } finally {
            executor.shutdown();
        }

        assertThat(leaveDeltas().stream().mapToInt(Integer::intValue).sum()).isEqualTo(-120);
        assertThat(balance()).isZero();
    }

    @Test
    void RICE_03_5_차감돼도_산_아이템은_계속_보유한다() throws Exception {
        Item hat = itemRepository.save(Item.create("LEAVE_HAT", "모자", 100, ItemSlot.HAT, 99));
        userItemRepository.save(UserItem.acquire(userA, hat));
        earn(room1, 200);
        purchase(100);

        leave(room1).andExpect(status().isOk());

        assertThat(leaveDeltas()).containsExactly(-100);
        assertThat(userItemRepository.existsByUser_IdAndItem_Id(userA.getId(), hat.getId())).isTrue();
    }

    @Test
    void RICE_03_11_내보내지면_그_방에서_모은_공양미를_차감한다() throws Exception {
        earn(room1, 200);
        earn(room2, 150);

        kick(room1, memberA).andExpect(status().isOk());

        assertThat(leaveDeltas()).containsExactly(-200);
        assertThat(balance()).isEqualTo(150);
    }

    @Test
    void RICE_03_12_미리보기와_실제_차감이_같다() throws Exception {
        earn(room1, 200);
        purchase(80);

        preview(room1).andExpect(status().isOk()).andExpect(jsonPath("$.data.riceToLose").value(120));
        leave(room1).andExpect(status().isOk());

        assertThat(leaveDeltas()).containsExactly(-120);
    }

    @Test
    void RICE_03_13_목표를_채운_방의_미리보기는_0석이다() throws Exception {
        earn(room1, 200);
        new TransactionTemplate(transactionManager).executeWithoutResult(status ->
                roomRepository.findById(room1.getId()).orElseThrow().completePot());

        preview(room1).andExpect(jsonPath("$.data.riceToLose").value(0));
    }

    /** 내보내기는 방장이 요청하지만 차감은 A의 잔액이라, A의 사용자 행 락으로 A의 다른 방 나가기와 하나씩 처리한다. */
    @Test
    void RICE_03_14_내보내기와_다른_방_나가기가_동시에_와도_잔액은_0석_아래로_내려가지_않는다() {
        earn(room1, 100);
        earn(room2, 100);
        purchase(80);

        CountDownLatch ready = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            CompletableFuture<Integer> kicked = CompletableFuture.supplyAsync(() -> {
                await(ready);
                return statusOfKick(room1, memberA);
            }, executor);
            CompletableFuture<Integer> left = CompletableFuture.supplyAsync(() -> {
                await(ready);
                return statusOf(room2);
            }, executor);
            ready.countDown();
            assertThat(kicked.join()).isEqualTo(200);
            assertThat(left.join()).isEqualTo(200);
        } finally {
            executor.shutdown();
        }

        assertThat(leaveDeltas().stream().mapToInt(Integer::intValue).sum()).isEqualTo(-120);
        assertThat(balance()).isZero();
    }

    @Test
    void 미리보기는_멤버가_아니면_ROOM_002_삭제된_방이면_ROOM_005다() throws Exception {
        Room other = newRoom();
        addMember(other, newUser(), "도윤");
        preview(other).andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("ROOM_002"));

        new TransactionTemplate(transactionManager).executeWithoutResult(status ->
                roomRepository.findById(other.getId()).orElseThrow().delete(LocalDateTime.now(clock)));
        preview(other).andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("ROOM_005"));
    }

    private ResultActions preview(Room room) throws Exception {
        return mockMvc.perform(get("/api/rooms/{roomId}/members/me/leave-preview", room.getId())
                .header("Authorization", "Bearer " + jwtProvider.createAccessToken(userA.getId())));
    }

    private ResultActions kick(Room room, RoomMember target) throws Exception {
        return mockMvc.perform(delete("/api/rooms/{roomId}/members/{memberId}", room.getId(), target.getId())
                .header("Authorization", "Bearer " + jwtProvider.createAccessToken(ownerB.getId())));
    }

    private int statusOfKick(Room room, RoomMember target) {
        try {
            return kick(room, target).andReturn().getResponse().getStatus();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private ResultActions leave(Room room) throws Exception {
        return mockMvc.perform(delete("/api/rooms/{roomId}/members/me", room.getId())
                .header("Authorization", "Bearer " + jwtProvider.createAccessToken(userA.getId())));
    }

    private void rejoin(RoomMember member) {
        new TransactionTemplate(transactionManager).executeWithoutResult(status ->
                roomMemberRepository.findById(member.getId()).orElseThrow()
                        .rejoin(member.getNickname(), BosalColor.PINK, LocalDateTime.now(clock)));
    }

    private int statusOf(Room room) {
        try {
            return leave(room).andReturn().getResponse().getStatus();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static void await(CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /** 인증 적립 하나를 기록하고 그 원인 ID(인증 ID 대신 쓰는 번호)를 돌려준다. */
    private Long earn(Room room, int amount) {
        Long refId = refSequence++;
        riceHistoryRepository.save(RiceHistory.create(
                userA, room, amount, RiceReason.CERTIFICATION, RiceRefType.CERTIFICATION, refId));
        return refId;
    }

    private void recall(Room room, Long refId, int amount) {
        riceHistoryRepository.save(RiceHistory.create(
                userA, room, -amount, RiceReason.RECALL, RiceRefType.CERTIFICATION, refId));
    }

    private void purchase(int amount) {
        riceHistoryRepository.save(RiceHistory.create(
                userA, null, -amount, RiceReason.PURCHASE, RiceRefType.ITEM, refSequence++));
    }

    private List<Integer> leaveDeltas() {
        return riceHistoryRepository.findAll().stream()
                .filter(h -> h.getReason() == RiceReason.LEAVE)
                .sorted((a, b) -> Long.compare(a.getId(), b.getId()))
                .map(RiceHistory::getDelta)
                .toList();
    }

    private long balance() {
        return riceHistoryRepository.sumDeltaByUserId(userA.getId());
    }

    private Room newRoom() {
        return roomRepository.save(Room.create("배달 끊기 방", RoomGoal.DELIVERY, 4, 300));
    }

    private User newUser() {
        return userRepository.save(User.create("anon-leave-" + userSequence++));
    }

    private RoomMember addMember(Room room, User user, String nickname) {
        return roomMemberRepository.save(RoomMember.create(room, user, nickname, BosalColor.PINK, DEC_1.minusDays(10).atStartOfDay()));
    }
}
