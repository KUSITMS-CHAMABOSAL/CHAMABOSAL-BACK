package com.chamabosal.domain.store.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.chamabosal.domain.defense.entity.DefenseTicket;
import com.chamabosal.domain.defense.repository.DefenseTicketRepository;
import com.chamabosal.domain.rice.entity.RiceHistory;
import com.chamabosal.domain.rice.entity.RiceReason;
import com.chamabosal.domain.rice.entity.RiceRefType;
import com.chamabosal.domain.rice.repository.RiceHistoryRepository;
import com.chamabosal.domain.room.entity.Room;
import com.chamabosal.domain.room.entity.RoomGoal;
import com.chamabosal.domain.room.repository.RoomRepository;
import com.chamabosal.domain.round.entity.Round;
import com.chamabosal.domain.round.repository.RoundRepository;
import com.chamabosal.domain.store.entity.Item;
import com.chamabosal.domain.store.entity.ItemSlot;
import com.chamabosal.domain.store.entity.UserItem;
import com.chamabosal.domain.store.repository.ItemRepository;
import com.chamabosal.domain.store.repository.UserItemRepository;
import com.chamabosal.domain.store.service.ItemCatalog;
import com.chamabosal.domain.store.service.ItemCatalogInitializer;
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
 * 곳간 조회(STORE-01), 아이템 구매(STORE-02), 착용·벗기(STORE-03). 시작 때 넣은 카탈로그를 지우고 시나리오마다 아이템을 직접 넣는다.
 * 지금은 2026-11-11(수) 12:00이고 이번 주는 11/9(월)~11/15(일)이다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(MutableClock.Config.class)
class StoreTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 11, 12, 0);

    @Autowired MockMvc mockMvc;
    @Autowired MutableClock clock;
    @Autowired JwtProvider jwtProvider;
    @Autowired UserRepository userRepository;
    @Autowired ItemRepository itemRepository;
    @Autowired UserItemRepository userItemRepository;
    @Autowired RiceHistoryRepository riceHistoryRepository;
    @Autowired DefenseTicketRepository defenseTicketRepository;
    @Autowired RoomRepository roomRepository;
    @Autowired RoundRepository roundRepository;
    @Autowired ItemCatalogInitializer itemCatalogInitializer;

    private int sequence;
    private User userA;

    @BeforeEach
    void setUp() {
        clock.set(NOW);
        userItemRepository.deleteAll();
        itemRepository.deleteAll();
        userA = newUser();
    }

    @AfterEach
    void tearDown() {
        defenseTicketRepository.deleteAll();
        roundRepository.deleteAll();
        roomRepository.deleteAll();
        riceHistoryRepository.deleteAll();
        userItemRepository.deleteAll();
        itemRepository.deleteAll();
        userRepository.deleteAll();
    }

    // ---------- STORE-01 곳간 조회 ----------

    @Test
    void STORE_01_1_아이템_6종이_노출_순서대로_오고_보유한_아이템만_보유_착용으로_표시된다() throws Exception {
        for (int order = 6; order >= 1; order--) {
            item("ITEM_" + order, ItemSlot.values()[order % 3], 100, order);
        }
        own(userA, itemRepository.findByCode("ITEM_3").orElseThrow(), true);

        store(userA)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items.length()").value(6))
                .andExpect(jsonPath("$.data.items[0].code").value("ITEM_1"))
                .andExpect(jsonPath("$.data.items[5].code").value("ITEM_6"))
                .andExpect(jsonPath("$.data.items[2].code").value("ITEM_3"))
                .andExpect(jsonPath("$.data.items[2].isOwned").value(true))
                .andExpect(jsonPath("$.data.items[2].isEquipped").value(true))
                .andExpect(jsonPath("$.data.items[0].isOwned").value(false))
                .andExpect(jsonPath("$.data.items[0].isEquipped").value(false));
    }

    @Test
    void STORE_01_2_음수_잔액은_0석으로_보인다() throws Exception {
        earn(userA, -10);

        store(userA).andExpect(jsonPath("$.data.riceBalance").value(0));
    }

    @Test
    void STORE_01_3_미사용_방어권이_있으면_OWNED다() throws Exception {
        defenseTicketRepository.save(DefenseTicket.purchase(userA, NOW.minusDays(1)));

        store(userA)
                .andExpect(jsonPath("$.data.defenseTicket.status").value("OWNED"))
                .andExpect(jsonPath("$.data.defenseTicket.isUsedThisWeek").value(false));
    }

    @Test
    void STORE_01_4_이번_주에_쓰고_남은_방어권이_없으면_NONE이고_이번_주_사용이다() throws Exception {
        useTicket(userA, LocalDate.of(2026, 11, 9));

        store(userA)
                .andExpect(jsonPath("$.data.defenseTicket.status").value("NONE"))
                .andExpect(jsonPath("$.data.defenseTicket.isUsedThisWeek").value(true));
    }

    @Test
    void 지난주에_쓴_방어권은_이번_주_사용이_아니다() throws Exception {
        useTicket(userA, LocalDate.of(2026, 11, 8));

        store(userA).andExpect(jsonPath("$.data.defenseTicket.isUsedThisWeek").value(false));
    }

    // ---------- STORE-02 아이템 구매 ----------

    @Test
    void STORE_02_1_사면_보유_착용_상태가_되고_잔액이_줄어든다() throws Exception {
        Item hat = item("SANTA_HAT", ItemSlot.HAT, 100, 1);
        earn(userA, 120);

        buy(userA, hat)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.riceBalance").value(20))
                .andExpect(jsonPath("$.data.equippedItemIds[0]").value(hat.getId()));

        assertThat(userItem(userA, hat).isEquipped()).isTrue();
        assertThat(balance(userA)).isEqualTo(20);
    }

    @Test
    void STORE_02_2_잔액과_가격이_같으면_사고_잔액은_0석이다() throws Exception {
        Item face = item("SMILE", ItemSlot.EXPRESSION, 50, 1);
        earn(userA, 50);

        buy(userA, face).andExpect(status().isCreated()).andExpect(jsonPath("$.data.riceBalance").value(0));
    }

    @Test
    void STORE_02_3_같은_슬롯에_끼고_있던_아이템은_벗고_보유로_남는다() throws Exception {
        Item x = item("SANTA_HAT", ItemSlot.HAT, 100, 1);
        Item y = item("CROWN", ItemSlot.HAT, 100, 2);
        Item nose = item("RUDOLPH_NOSE", ItemSlot.FACE, 100, 3);
        own(userA, x, true);
        own(userA, nose, true);
        earn(userA, 100);

        buy(userA, y)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.equippedItemIds.length()").value(2))
                .andExpect(jsonPath("$.data.equippedItemIds[0]").value(y.getId()))
                .andExpect(jsonPath("$.data.equippedItemIds[1]").value(nose.getId()));

        assertThat(userItem(userA, y).isEquipped()).isTrue();
        assertThat(userItem(userA, x).isEquipped()).isFalse();
        assertThat(userItem(userA, nose).isEquipped()).isTrue();
    }

    @Test
    void STORE_02_4_잔액이_부족하면_STORE_001이고_장부에_기록하지_않는다() throws Exception {
        Item hat = item("SANTA_HAT", ItemSlot.HAT, 100, 1);
        earn(userA, 90);

        buy(userA, hat).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("STORE_001"));

        assertThat(purchases(userA)).isZero();
        assertThat(userItemRepository.existsByUser_IdAndItem_Id(userA.getId(), hat.getId())).isFalse();
    }

    @Test
    void STORE_02_5_음수_잔액이면_STORE_001이다() throws Exception {
        Item smile = item("SMILE", ItemSlot.EXPRESSION, 50, 1);
        earn(userA, -5);

        buy(userA, smile).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("STORE_001"));
    }

    @Test
    void STORE_02_6_없는_아이템이면_STORE_002다() throws Exception {
        earn(userA, 1000);

        mockMvc.perform(post("/api/items/{itemId}/purchases", 999_999L).header("Authorization", bearer(userA)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("STORE_002"));
    }

    @Test
    void STORE_02_7_이미_보유했으면_STORE_003이고_잔액은_그대로다() throws Exception {
        Item hat = item("SANTA_HAT", ItemSlot.HAT, 100, 1);
        own(userA, hat, false);
        earn(userA, 200);

        buy(userA, hat).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("STORE_003"));

        assertThat(balance(userA)).isEqualTo(200);
    }

    @Test
    void STORE_02_8_같은_아이템을_동시에_사면_한_번만_사고_한_번만_차감한다() {
        Item hat = item("SANTA_HAT", ItemSlot.HAT, 100, 1);
        earn(userA, 200);

        List<Integer> statuses = concurrently(() -> statusOf(() -> buy(userA, hat)), () -> statusOf(() -> buy(userA, hat)));

        assertThat(statuses).containsExactlyInAnyOrder(201, 409);
        assertThat(userItemRepository.count()).isEqualTo(1);
        assertThat(balance(userA)).isEqualTo(100);
    }

    @Test
    void STORE_02_9_다른_아이템을_동시에_사면_하나만_사고_다른_요청은_STORE_001이다() {
        Item hat = item("SANTA_HAT", ItemSlot.HAT, 100, 1);
        Item nose = item("RUDOLPH_NOSE", ItemSlot.FACE, 100, 2);
        earn(userA, 120);

        List<ResultActions> results = concurrentlyResults(() -> buy(userA, hat), () -> buy(userA, nose));

        assertThat(results.stream().map(result -> result.andReturn().getResponse().getStatus()))
                .containsExactlyInAnyOrder(201, 409);
        assertThat(results.stream().map(StoreTest::bodyOf).filter(body -> body.contains("STORE_001"))).hasSize(1);
        assertThat(userItemRepository.count()).isEqualTo(1);
        assertThat(balance(userA)).isEqualTo(20);
    }

    // ---------- STORE-03 착용 · 벗기 ----------

    @Test
    void STORE_03_1_보낸_아이템을_착용한다() throws Exception {
        Item x = item("SANTA_HAT", ItemSlot.HAT, 100, 1);
        own(userA, x, false);

        equip(userA, x.getId()).andExpect(status().isOk());

        assertThat(userItem(userA, x).isEquipped()).isTrue();
    }

    @Test
    void STORE_03_2_같은_슬롯의_다른_아이템으로_바꾸면_그것만_착용한다() throws Exception {
        Item x = item("SANTA_HAT", ItemSlot.HAT, 100, 1);
        Item y = item("CROWN", ItemSlot.HAT, 100, 2);
        own(userA, x, true);
        own(userA, y, false);

        equip(userA, y.getId()).andExpect(status().isOk());

        assertThat(userItem(userA, y).isEquipped()).isTrue();
        assertThat(userItem(userA, x).isEquipped()).isFalse();
    }

    @Test
    void STORE_03_3_빈_목록이면_모두_벗는다() throws Exception {
        Item x = item("SANTA_HAT", ItemSlot.HAT, 100, 1);
        own(userA, x, true);

        equip(userA).andExpect(status().isOk());

        assertThat(userItem(userA, x).isEquipped()).isFalse();
    }

    @Test
    void STORE_03_4_보유하지_않은_아이템이면_STORE_004이고_착용은_그대로다() throws Exception {
        Item x = item("SANTA_HAT", ItemSlot.HAT, 100, 1);
        Item y = item("RUDOLPH_NOSE", ItemSlot.FACE, 100, 2);
        own(userA, x, true);

        equip(userA, y.getId()).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("STORE_004"));

        assertThat(userItem(userA, x).isEquipped()).isTrue();
    }

    @Test
    void 없는_아이템도_보유하지_않은_아이템으로_본다() throws Exception {
        equip(userA, 999_999L).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("STORE_004"));
    }

    @Test
    void STORE_03_6_같은_슬롯을_동시에_바꾸면_하나만_착용한다() {
        Item x = item("SANTA_HAT", ItemSlot.HAT, 100, 1);
        Item y = item("CROWN", ItemSlot.HAT, 100, 2);
        own(userA, x, false);
        own(userA, y, false);

        List<Integer> statuses = concurrently(() -> statusOf(() -> equip(userA, x.getId())),
                () -> statusOf(() -> equip(userA, y.getId())));

        assertThat(statuses).containsExactly(200, 200);
        assertThat(userItemRepository.findAll().stream().filter(UserItem::isEquipped)).hasSize(1);
    }

    @Test
    void STORE_03_7_같은_슬롯_아이템을_둘_보내면_COMMON_002이고_착용은_그대로다() throws Exception {
        Item x = item("SANTA_HAT", ItemSlot.HAT, 100, 1);
        Item y = item("CROWN", ItemSlot.HAT, 100, 2);
        own(userA, x, true);
        own(userA, y, false);

        equip(userA, x.getId(), y.getId())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON_002"));

        assertThat(userItem(userA, x).isEquipped()).isTrue();
        assertThat(userItem(userA, y).isEquipped()).isFalse();
    }

    @Test
    void 같은_아이템을_두_번_보내면_COMMON_002다() throws Exception {
        Item x = item("SANTA_HAT", ItemSlot.HAT, 100, 1);
        own(userA, x, false);

        equip(userA, x.getId(), x.getId())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON_002"));
    }

    @Test
    void 목록이_없거나_4개_이상이면_COMMON_002다() throws Exception {
        mockMvc.perform(put("/api/users/me/equipped-items").header("Authorization", bearer(userA))
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON_002"));
        equip(userA, 1L, 2L, 3L, 4L)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON_002"));
    }

    // ---------- 다른 기능과의 연결 ----------

    @Test
    void 내_정보에_착용_아이템_코드가_노출_순서대로_온다() throws Exception {
        Item nose = item("RUDOLPH_NOSE", ItemSlot.FACE, 100, 2);
        Item hat = item("SANTA_HAT", ItemSlot.HAT, 100, 1);
        Item crown = item("CROWN", ItemSlot.HAT, 100, 3);
        own(userA, nose, true);
        own(userA, hat, true);
        own(userA, crown, false);

        mockMvc.perform(get("/api/users/me").header("Authorization", bearer(userA)))
                .andExpect(jsonPath("$.data.equippedItemCodes.length()").value(2))
                .andExpect(jsonPath("$.data.equippedItemCodes[0]").value("SANTA_HAT"))
                .andExpect(jsonPath("$.data.equippedItemCodes[1]").value("RUDOLPH_NOSE"));
    }

    @Test
    void 내_데이터를_삭제하면_보유_아이템도_삭제한다() throws Exception {
        Item hat = item("SANTA_HAT", ItemSlot.HAT, 100, 1);
        own(userA, hat, true);

        mockMvc.perform(delete("/api/users/me").header("Authorization", bearer(userA))).andExpect(status().isOk());

        assertThat(userItemRepository.existsByUser_IdAndItem_Id(userA.getId(), hat.getId())).isFalse();
        assertThat(itemRepository.findById(hat.getId())).isPresent();
    }

    @Test
    void 카탈로그는_없으면_넣고_있으면_고치고_뺀_아이템은_남긴다() {
        Item old = item("SANTA_HAT", ItemSlot.FACE, 30, 9);
        Item retired = item("RETIRED", ItemSlot.HAT, 100, 10);
        own(userA, old, true);
        own(userA, retired, true);

        itemCatalogInitializer.sync();

        List<Item> items = itemRepository.findAllByOrderByDisplayOrderAscIdAsc();
        for (ItemCatalog.Entry entry : ItemCatalog.ENTRIES) {
            Item synced = itemRepository.findByCode(entry.code()).orElseThrow();
            assertThat(synced.getName()).isEqualTo(entry.name());
            assertThat(synced.getPrice()).isEqualTo(entry.price());
            assertThat(synced.getSlot()).isEqualTo(entry.slot());
            assertThat(synced.getDisplayOrder()).isEqualTo(entry.displayOrder());
        }
        assertThat(itemRepository.findByCode("SANTA_HAT").orElseThrow().getId()).isEqualTo(old.getId());
        assertThat(itemRepository.findById(retired.getId())).isPresent();
        assertThat(items).hasSize(ItemCatalog.ENTRIES.size() + 1);
        // 슬롯이 FACE에서 HAT으로 바뀌어 벗기고, 이미 HAT에 착용한 아이템은 그대로다 (STORE-03 3번).
        assertThat(userItem(userA, old).isEquipped()).isFalse();
        assertThat(userItem(userA, retired).isEquipped()).isTrue();
    }

    // ---------- 준비 ----------

    private User newUser() {
        return userRepository.save(User.create("anon-store-" + sequence++));
    }

    private Item item(String code, ItemSlot slot, int price, int displayOrder) {
        return itemRepository.save(Item.create(code, code, price, slot, displayOrder));
    }

    private void own(User user, Item item, boolean equipped) {
        UserItem userItem = UserItem.acquire(user, item);
        if (!equipped) {
            userItem.unequip();
        }
        userItemRepository.save(userItem);
    }

    private UserItem userItem(User user, Item item) {
        return userItemRepository.findAll().stream()
                .filter(found -> found.getUser().getId().equals(user.getId())
                        && found.getItem().getId().equals(item.getId()))
                .findFirst().orElseThrow();
    }

    private void earn(User user, int amount) {
        riceHistoryRepository.save(RiceHistory.create(
                user, null, amount, RiceReason.CERTIFICATION, RiceRefType.CERTIFICATION, (long) sequence++));
    }

    private long balance(User user) {
        return riceHistoryRepository.sumDeltaByUserId(user.getId());
    }

    private long purchases(User user) {
        return riceHistoryRepository.findAll().stream()
                .filter(history -> history.getUser().getId().equals(user.getId())
                        && history.getReason() == RiceReason.PURCHASE)
                .count();
    }

    private void useTicket(User user, LocalDate roundDate) {
        Room room = roomRepository.save(Room.create("배달 끊기 방", RoomGoal.DELIVERY, 4, 300));
        Round round = roundRepository.save(Round.create(room, roundDate));
        DefenseTicket ticket = DefenseTicket.purchase(user, roundDate.atTime(10, 0));
        ticket.use(round, roundDate.atTime(16, 0));
        defenseTicketRepository.save(ticket);
    }

    private ResultActions store(User user) throws Exception {
        return mockMvc.perform(get("/api/store").header("Authorization", bearer(user)));
    }

    private ResultActions buy(User user, Item item) throws Exception {
        return mockMvc.perform(post("/api/items/{itemId}/purchases", item.getId()).header("Authorization", bearer(user)));
    }

    private ResultActions equip(User user, Long... itemIds) throws Exception {
        String ids = String.join(", ", java.util.Arrays.stream(itemIds).map(String::valueOf).toList());
        return mockMvc.perform(put("/api/users/me/equipped-items").header("Authorization", bearer(user))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"itemIds\": [" + ids + "]}"));
    }

    private String bearer(User user) {
        return "Bearer " + jwtProvider.createAccessToken(user.getId());
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

    private static String bodyOf(ResultActions result) {
        try {
            return result.andReturn().getResponse().getContentAsString();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static List<ResultActions> concurrentlyResults(Call first, Call second) {
        return concurrently(() -> run(first), () -> run(second));
    }

    private static ResultActions run(Call call) {
        try {
            return call.run();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static <T> List<T> concurrently(Supplier<T> first, Supplier<T> second) {
        CountDownLatch ready = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            CompletableFuture<T> one = CompletableFuture.supplyAsync(() -> {
                await(ready);
                return first.get();
            }, executor);
            CompletableFuture<T> two = CompletableFuture.supplyAsync(() -> {
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
}
