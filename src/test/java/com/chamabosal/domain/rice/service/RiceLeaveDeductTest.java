package com.chamabosal.domain.rice.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
 * 한 솥(POT-01)이 아직 없어 모든 방은 목표를 채우기 전이다.
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

    private int userSequence;
    private long refSequence = 1;
    private Room room1;
    private Room room2;
    private User userA;
    private RoomMember memberA;

    @BeforeEach
    void setUp() {
        clock.set(T0);
        room1 = newRoom();
        room2 = newRoom();
        userA = newUser();
        memberA = addMember(room1, userA, "민지");
        addMember(room1, newUser(), "지수");
        addMember(room2, userA, "민지");
        addMember(room2, newUser(), "하린");
    }

    @AfterEach
    void tearDown() {
        riceHistoryRepository.deleteAll();
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
        return roomRepository.save(Room.create("배달 끊기 방", RoomGoal.DELIVERY, 4));
    }

    private User newUser() {
        return userRepository.save(User.create("anon-leave-" + userSequence++));
    }

    private RoomMember addMember(Room room, User user, String nickname) {
        return roomMemberRepository.save(RoomMember.create(room, user, nickname, BosalColor.PINK, DEC_1.minusDays(10).atStartOfDay()));
    }
}
