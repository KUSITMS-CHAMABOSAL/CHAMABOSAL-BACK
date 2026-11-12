package com.chamabosal.domain.room.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.chamabosal.domain.room.entity.Room;
import com.chamabosal.domain.user.entity.User;
import com.jayway.jsonpath.JsonPath;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.ResultActions;

class InviteTokenTest extends InviteTestSupport {

    private User userA;
    private Room room;

    @BeforeEach
    void setUp() {
        userA = newUser();
        room = createRoom(4);
        addMember(room, userA, "민지");
    }

    @Test
    void INVITE_01_1_토큰이_없으면_만들어_돌려준다() throws Exception {
        String body = issue(userA)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.inviteToken").isString())
                .andReturn().getResponse().getContentAsString();

        String token = JsonPath.read(body, "$.data.inviteToken");
        assertThat(token).hasSizeGreaterThanOrEqualTo(32);
        assertThat(tokenOf(userA)).isEqualTo(token);
    }

    @Test
    void INVITE_01_2_토큰이_있으면_같은_토큰을_돌려준다() throws Exception {
        issue(userA).andExpect(status().isCreated());
        String token = tokenOf(userA);

        issue(userA)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.inviteToken").value(token));
    }

    @Test
    void INVITE_01_3_방_멤버가_아니면_ROOM_002를_돌려준다() throws Exception {
        User userD = newUser();

        issue(userD)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ROOM_002"));
    }

    @Test
    void INVITE_01_4_동시에_요청해도_토큰은_하나다() {
        int requestCount = 2;
        ExecutorService executor = Executors.newFixedThreadPool(requestCount);
        try {
            CountDownLatch ready = new CountDownLatch(requestCount);
            CountDownLatch start = new CountDownLatch(1);
            List<CompletableFuture<String>> futures = IntStream.range(0, requestCount)
                    .mapToObj(i -> CompletableFuture.supplyAsync(() -> {
                        ready.countDown();
                        await(start);
                        return inviteService.issueToken(userA.getId(), room.getId()).response().inviteToken();
                    }, executor))
                    .toList();
            await(ready);
            start.countDown();
            List<String> tokens = futures.stream().map(CompletableFuture::join).toList();

            assertThat(tokens).containsOnly(tokenOf(userA));
        } finally {
            executor.shutdown();
        }
    }

    private ResultActions issue(User user) throws Exception {
        return mockMvc.perform(post("/api/rooms/{roomId}/members/me/invite-token", room.getId())
                .header("Authorization", bearer(user)));
    }

    private String tokenOf(User user) {
        return roomMemberRepository.findByRoom_IdAndUser_IdAndLeftAtIsNull(room.getId(), user.getId())
                .orElseThrow()
                .getInviteToken();
    }
}
