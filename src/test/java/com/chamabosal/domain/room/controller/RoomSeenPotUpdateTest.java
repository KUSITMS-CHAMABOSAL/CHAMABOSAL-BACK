package com.chamabosal.domain.room.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.chamabosal.domain.room.dto.request.RoomSeenPotUpdateRequest;
import com.chamabosal.domain.room.entity.LeaveType;
import com.chamabosal.domain.room.entity.Room;
import com.chamabosal.domain.room.entity.RoomMember;
import com.chamabosal.domain.user.entity.User;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.support.TransactionTemplate;

/** 오늘 탭(ROOM-02)이 아직 없어 "안 본 축하 여부"는 저장값으로 확인한다. 완성 횟수는 POT-01이 없어 직접 넣는다. */
class RoomSeenPotUpdateTest extends RoomTestSupport {

    @Autowired
    private TransactionTemplate transactionTemplate;

    private User userA;
    private Room room;
    private RoomMember memberA;

    @BeforeEach
    void setUp() {
        userA = newUser();
        room = createRoom(4);
        memberA = addMember(room, userA, "민지");
    }

    @Test
    void ROOM_06_4_완성_횟수까지_봤다고_확인하면_저장한다() throws Exception {
        completePots(2);
        setSeenPotCount(1);

        markSeen(2)
                .andExpect(status().isOk());

        RoomMember saved = roomMemberRepository.findById(memberA.getId()).orElseThrow();
        assertThat(saved.getSeenPotCount()).isEqualTo(2);
        assertThat(saved.getUpdatedAt()).isEqualTo(NOW);
    }

    @Test
    void ROOM_06_5_보는_중에_한_솥이_또_완성되면_본_횟수까지만_저장한다() throws Exception {
        completePots(2);

        markSeen(1)
                .andExpect(status().isOk());

        assertThat(seenPotCount()).isEqualTo(1);
        assertThat(roomRepository.findById(room.getId()).orElseThrow().getPotCompletedCount()).isEqualTo(2);
    }

    @Test
    void 방의_완성_횟수보다_크면_COMMON_002를_돌려주고_저장하지_않는다() throws Exception {
        completePots(2);

        markSeen(3)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON_002"))
                .andExpect(jsonPath("$.field").value("seenPotCount"));

        assertThat(seenPotCount()).isZero();
    }

    @Test
    void 음수면_COMMON_002를_돌려준다() throws Exception {
        markSeen(-1)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON_002"))
                .andExpect(jsonPath("$.field").value("seenPotCount"));
    }

    @Test
    void 값이_없으면_COMMON_002를_돌려준다() throws Exception {
        mockMvc.perform(put("/api/rooms/{roomId}/members/me/seen-pot", room.getId())
                        .header("Authorization", bearer(userA))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON_002"))
                .andExpect(jsonPath("$.field").value("seenPotCount"));
    }

    @Test
    void 나간_멤버면_ROOM_002를_돌려준다() throws Exception {
        completePots(1);
        addMember(room, newUser(), "지수");
        leave(memberA.getId(), NOW.minusDays(1), LeaveType.LEFT);

        markSeen(1)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ROOM_002"));
    }

    @Test
    void 삭제된_방이면_ROOM_005를_돌려준다() throws Exception {
        completePots(1);
        deleteRoom(room);

        markSeen(1)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ROOM_005"));
    }

    @Test
    void 저장값보다_작거나_같으면_바꾸지_않는다() throws Exception {
        completePots(2);
        markSeen(2).andExpect(status().isOk());

        markSeen(2).andExpect(status().isOk());
        markSeen(1).andExpect(status().isOk());

        assertThat(seenPotCount()).isEqualTo(2);
    }

    @Test
    void 확인_뒤에_다른_트랜잭션이_멤버를_저장해도_확인_횟수를_되돌리지_않는다() {
        completePots(2);

        // 닉네임을 바꾸는 트랜잭션이 멤버를 읽은 뒤, 그사이 확인 요청이 먼저 커밋되는 순서를 만든다.
        transactionTemplate.executeWithoutResult(status -> {
            RoomMember stale = roomMemberRepository.findById(memberA.getId()).orElseThrow();
            CompletableFuture.runAsync(() ->
                    roomService.markPotSeen(userA.getId(), room.getId(), new RoomSeenPotUpdateRequest(2))).join();
            stale.changeNickname("민지2");
        });

        RoomMember saved = roomMemberRepository.findById(memberA.getId()).orElseThrow();
        assertThat(saved.getNickname()).isEqualTo("민지2");
        assertThat(saved.getSeenPotCount()).isEqualTo(2);
    }

    @Test
    void 이미_본_횟수보다_작은_값이_동시에_와도_큰_값이_남는다() {
        // 커넥션 풀(기본 10개)을 넘지 않게 한다.
        int requestCount = 10;
        completePots(requestCount);
        ExecutorService executor = Executors.newFixedThreadPool(requestCount);
        try {
            CountDownLatch ready = new CountDownLatch(requestCount);
            CountDownLatch start = new CountDownLatch(1);
            // 큰 값부터 보내 작은 값이 나중에 저장되기 쉽게 한다.
            List<CompletableFuture<Void>> futures = IntStream.rangeClosed(1, requestCount)
                    .map(i -> requestCount + 1 - i)
                    .mapToObj(count -> CompletableFuture.runAsync(() -> {
                        ready.countDown();
                        await(start);
                        roomService.markPotSeen(userA.getId(), room.getId(), new RoomSeenPotUpdateRequest(count));
                    }, executor))
                    .toList();
            await(ready);
            start.countDown();
            futures.forEach(CompletableFuture::join);

            assertThat(seenPotCount()).isEqualTo(requestCount);
        } finally {
            executor.shutdown();
        }
    }

    private ResultActions markSeen(int seenPotCount) throws Exception {
        return mockMvc.perform(put("/api/rooms/{roomId}/members/me/seen-pot", room.getId())
                .header("Authorization", bearer(userA))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"seenPotCount\":%d}".formatted(seenPotCount)));
    }

    private void completePots(int count) {
        ReflectionTestUtils.setField(room, "potCompletedCount", count);
        roomRepository.save(room);
    }

    private void setSeenPotCount(int count) {
        ReflectionTestUtils.setField(memberA, "seenPotCount", count);
        roomMemberRepository.save(memberA);
    }

    private int seenPotCount() {
        return roomMemberRepository.findById(memberA.getId()).orElseThrow().getSeenPotCount();
    }
}
