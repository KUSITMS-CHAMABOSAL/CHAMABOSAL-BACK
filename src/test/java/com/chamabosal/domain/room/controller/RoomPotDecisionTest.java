package com.chamabosal.domain.room.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.chamabosal.domain.pot.entity.RoomPot;
import com.chamabosal.domain.rice.entity.RiceHistory;
import com.chamabosal.domain.rice.entity.RiceReason;
import com.chamabosal.domain.rice.entity.RiceRefType;
import com.chamabosal.domain.room.dto.request.RoomCreateRequest;
import com.chamabosal.domain.room.entity.BosalColor;
import com.chamabosal.domain.room.entity.LeaveType;
import com.chamabosal.domain.room.entity.Room;
import com.chamabosal.domain.room.entity.RoomGoal;
import com.chamabosal.domain.room.entity.RoomMember;
import com.chamabosal.domain.user.entity.User;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** 목표 달성 뒤 선택 (ROOM-09). 공통 Given: 목표 300석인 방에 방장 A, B, C가 이 순서로 들어와 있다. */
class RoomPotDecisionTest extends RoomTestSupport {

    @Autowired
    PlatformTransactionManager transactionManager;

    private User userA;
    private User userB;
    private User userC;
    private Room room;

    @BeforeEach
    void setUp() {
        userA = newUser();
        userB = newUser();
        userC = newUser();
        Long roomId = roomService.create(userA.getId(),
                new RoomCreateRequest("배달 끊기 방", RoomGoal.DELIVERY, 4, 300, "민지", BosalColor.PINK)).roomId();
        room = roomRepository.findById(roomId).orElseThrow();
        addMember(room, userB, "지수");
        addMember(room, userC, "하린");
    }

    @Test
    void ROOM_09_1_솥을_채우면_방장에게만_선택_대기다() throws Exception {
        potAt(295);

        certify(userB, room).andExpect(status().isCreated());

        today(userA).andExpect(jsonPath("$.data.me.isPotDecisionPending").value(true));
        today(userB).andExpect(jsonPath("$.data.me.isPotDecisionPending").value(false));
    }

    @Test
    void ROOM_09_2_더_수행하기면_선택_대기가_끝나고_방은_그대로다() throws Exception {
        potAt(295);
        certify(userB, room).andExpect(status().isCreated());

        continuePot(userA).andExpect(status().isOk());

        assertThat(reloadRoom().isPotDecisionPending()).isFalse();
        assertThat(latestPot().getAmount()).isEqualTo(5);
        assertThat(roomMemberRepository.findByRoom_IdAndLeftAtIsNull(room.getId())).hasSize(3);
        today(userA).andExpect(jsonPath("$.data.me.isPotDecisionPending").value(false));
    }

    @Test
    void ROOM_09_3_방_닫기면_방이_삭제된다() throws Exception {
        markPending();

        close(userA).andExpect(status().isOk());

        assertThat(reloadRoom().isDeleted()).isTrue();
        assertThat(roomMemberRepository.findByRoom_IdAndLeftAtIsNull(room.getId())).isEmpty();
        assertThat(roomMemberRepository.findAll()).filteredOn(member -> member.getRoom().getId().equals(room.getId()))
                .extracting(RoomMember::getLeaveType).containsOnly(LeaveType.ROOM_CLOSED);
        today(userB).andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("ROOM_005"));
    }

    @Test
    void ROOM_09_4_방장이_아니면_ROOM_007이다() throws Exception {
        markPending();

        continuePot(userB).andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("ROOM_007"));
        close(userB).andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("ROOM_007"));

        assertThat(reloadRoom().isPotDecisionPending()).isTrue();
        assertThat(reloadRoom().isDeleted()).isFalse();
    }

    @Test
    void ROOM_09_5_선택_대기가_아니면_ROOM_011이다() throws Exception {
        continuePot(userA).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("ROOM_011"));
        close(userA).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("ROOM_011"));

        assertThat(reloadRoom().isDeleted()).isFalse();
    }

    @Test
    void ROOM_09_6_선택_대기_중에도_인증하면_다음_솥에_쌓인다() throws Exception {
        potAt(295);
        certify(userB, room).andExpect(status().isCreated());

        certify(userC, room).andExpect(status().isCreated());

        assertThat(reloadRoom().isPotDecisionPending()).isTrue();
        assertThat(latestPot().getSeq()).isEqualTo(2);
        assertThat(latestPot().getAmount()).isEqualTo(15);
    }

    @Test
    void ROOM_09_7_방장이_나가면_위임된_방장이_고른다() throws Exception {
        markPending();

        mockMvc.perform(delete("/api/rooms/{roomId}/members/me", room.getId()).header("Authorization", bearer(userA)))
                .andExpect(status().isOk());

        today(userB).andExpect(jsonPath("$.data.room.isOwner").value(true))
                .andExpect(jsonPath("$.data.me.isPotDecisionPending").value(true));
        continuePot(userB).andExpect(status().isOk());
    }

    @Test
    void ROOM_09_8_선택_대기_중_솥을_또_채워도_한_번만_고른다() throws Exception {
        potAt(295);
        certify(userB, room).andExpect(status().isCreated());
        setLatestPotAmount(295);
        certify(userC, room).andExpect(status().isCreated());
        assertThat(reloadRoom().getPotCompletedCount()).isEqualTo(2);

        continuePot(userA).andExpect(status().isOk());

        assertThat(reloadRoom().isPotDecisionPending()).isFalse();
        continuePot(userA).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("ROOM_011"));
    }

    @Test
    void ROOM_09_9_방_닫기로는_공양미를_차감하지_않는다() throws Exception {
        riceHistoryRepository.save(RiceHistory.create(userB, room, 200, RiceReason.CERTIFICATION, RiceRefType.CERTIFICATION, -1L));
        markPending();

        close(userA).andExpect(status().isOk());

        assertThat(riceHistoryRepository.findAll()).extracting(RiceHistory::getReason).doesNotContain(RiceReason.LEAVE);
        mockMvc.perform(get("/api/store").header("Authorization", bearer(userB)))
                .andExpect(jsonPath("$.data.riceBalance").value(200));
    }

    @Test
    void ROOM_09_10_동시에_골라도_하나만_처리된다() {
        markPending();
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        int continued;
        int closed;
        try {
            CompletableFuture<Integer> continueResult =
                    CompletableFuture.supplyAsync(() -> statusAfter(start, () -> continuePot(userA)), executor);
            CompletableFuture<Integer> closeResult =
                    CompletableFuture.supplyAsync(() -> statusAfter(start, () -> close(userA)), executor);
            start.countDown();
            continued = continueResult.join();
            closed = closeResult.join();
        } finally {
            executor.shutdown();
        }

        Room reloaded = reloadRoom();
        if (reloaded.isDeleted()) {
            // 방 닫기가 먼저: 더 수행하기는 삭제된 방이라 ROOM_005
            assertThat(List.of(closed, continued)).containsExactly(200, 404);
        } else {
            // 더 수행하기가 먼저: 방 닫기는 선택 대기가 끝나 ROOM_011
            assertThat(List.of(continued, closed)).containsExactly(200, 409);
            assertThat(reloaded.isPotDecisionPending()).isFalse();
        }
    }

    private interface Request {
        ResultActions perform() throws Exception;
    }

    private int statusAfter(CountDownLatch start, Request request) {
        await(start);
        try {
            return request.perform().andReturn().getResponse().getStatus();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private void potAt(int amount) {
        RoomPot pot = RoomPot.first(room);
        pot.add(amount);
        roomPotRepository.save(pot);
    }

    private void setLatestPotAmount(int amount) {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            RoomPot pot = roomPotRepository.findFirstByRoom_IdOrderBySeqDesc(room.getId()).orElseThrow();
            pot.add(amount - pot.getAmount());
        });
    }

    private void markPending() {
        Room loaded = reloadRoom();
        loaded.requestPotDecision(NOW);
        roomRepository.save(loaded);
    }

    private Room reloadRoom() {
        return roomRepository.findById(room.getId()).orElseThrow();
    }

    private RoomPot latestPot() {
        return roomPotRepository.findFirstByRoom_IdOrderBySeqDesc(room.getId()).orElseThrow();
    }

    private ResultActions today(User user) throws Exception {
        return mockMvc.perform(get("/api/rooms/{roomId}/today", room.getId()).header("Authorization", bearer(user)));
    }

    private ResultActions continuePot(User user) throws Exception {
        return mockMvc.perform(post("/api/rooms/{roomId}/pot-decision/continue", room.getId())
                .header("Authorization", bearer(user)));
    }

    private ResultActions close(User user) throws Exception {
        return mockMvc.perform(delete("/api/rooms/{roomId}", room.getId()).header("Authorization", bearer(user)));
    }
}
