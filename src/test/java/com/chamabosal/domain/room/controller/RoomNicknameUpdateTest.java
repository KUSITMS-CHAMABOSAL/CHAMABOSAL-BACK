package com.chamabosal.domain.room.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.chamabosal.domain.room.dto.request.RoomCreateRequest;
import com.chamabosal.domain.room.dto.request.RoomNicknameUpdateRequest;
import com.chamabosal.domain.room.entity.BosalColor;
import com.chamabosal.domain.room.entity.LeaveType;
import com.chamabosal.domain.room.entity.Room;
import com.chamabosal.domain.room.entity.RoomGoal;
import com.chamabosal.domain.room.entity.RoomMember;
import com.chamabosal.domain.room.repository.RoomMemberRepository;
import com.chamabosal.domain.room.repository.RoomRepository;
import com.chamabosal.domain.room.service.RoomService;
import com.chamabosal.domain.user.entity.User;
import com.chamabosal.domain.user.repository.UserRepository;
import com.chamabosal.global.common.exception.BaseErrorCode;
import com.chamabosal.global.common.exception.BaseException;
import com.chamabosal.global.config.ClockConfig;
import com.chamabosal.global.security.jwt.JwtProvider;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZonedDateTime;
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
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

@SpringBootTest
@AutoConfigureMockMvc
class RoomNicknameUpdateTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 11, 12, 0);

    @TestConfiguration
    static class FixedClockConfig {

        @Bean
        @Primary
        Clock fixedClock() {
            return Clock.fixed(ZonedDateTime.of(NOW, ClockConfig.KST).toInstant(), ClockConfig.KST);
        }
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private RoomService roomService;

    @Autowired
    private RoomRepository roomRepository;

    @Autowired
    private RoomMemberRepository roomMemberRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JwtProvider jwtProvider;

    private User userA;
    private User userB;
    private Room room;

    @BeforeEach
    void setUp() {
        userA = userRepository.save(User.create("anon-a"));
        userB = userRepository.save(User.create("anon-b"));
        Long roomId = roomService.create(userA.getId(),
                new RoomCreateRequest("배달 끊기 방", RoomGoal.DELIVERY, 4, "민지")).roomId();
        room = roomRepository.findById(roomId).orElseThrow();
    }

    @AfterEach
    void tearDown() {
        roomMemberRepository.deleteAll();
        roomRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    void ROOM_04_1_닉네임을_바꾼다() throws Exception {
        update(userA, "민지2")
                .andExpect(status().isOk());

        assertThat(nicknameOf(userA)).isEqualTo("민지2");
    }

    @Test
    void ROOM_04_2_현재_멤버와_대소문자만_달라도_쓸_수_없다() throws Exception {
        addMember(userB, "Jisu", BosalColor.MINT);

        update(userA, "jisu")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ROOM_004"))
                .andExpect(jsonPath("$.field").value("nickname"));

        assertThat(nicknameOf(userA)).isEqualTo("민지");
    }

    @Test
    void ROOM_04_3_나간_지_6개월이_안_된_멤버의_닉네임은_쓸_수_없다() throws Exception {
        addLeftMember("하린", NOW.minusMonths(5));

        update(userA, "하린")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ROOM_004"));

        assertThat(nicknameOf(userA)).isEqualTo("민지");
    }

    @Test
    void ROOM_04_4_나간_지_6개월이_지난_멤버의_닉네임은_쓸_수_있다() throws Exception {
        addLeftMember("하린", NOW.minusMonths(7));

        update(userA, "하린")
                .andExpect(status().isOk());

        assertThat(nicknameOf(userA)).isEqualTo("하린");
    }

    @Test
    void ROOM_04_5_내_닉네임의_대소문자만_바꿀_수_있다() throws Exception {
        update(userA, "jisu").andExpect(status().isOk());

        update(userA, "Jisu")
                .andExpect(status().isOk());

        assertThat(nicknameOf(userA)).isEqualTo("Jisu");
    }

    @Test
    void ROOM_04_6_지금과_같은_닉네임이면_그대로_성공한다() throws Exception {
        update(userA, "민지")
                .andExpect(status().isOk());

        assertThat(nicknameOf(userA)).isEqualTo("민지");
    }

    @Test
    void ROOM_04_7_같은_닉네임으로_동시에_바꾸면_한_명만_성공한다() {
        addMember(userB, "지수", BosalColor.MINT);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            List<Boolean> results = updateConcurrently(executor, List.of(userA, userB), "하린");

            assertThat(results).containsExactlyInAnyOrder(true, false);
            assertThat(List.of(nicknameOf(userA), nicknameOf(userB))).containsOnlyOnce("하린");
        } finally {
            executor.shutdown();
        }
    }

    private ResultActions update(User user, String nickname) throws Exception {
        return mockMvc.perform(patch("/api/rooms/{roomId}/members/me/nickname", room.getId())
                .header("Authorization", "Bearer " + jwtProvider.createAccessToken(user.getId()))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"nickname\":\"%s\"}".formatted(nickname)));
    }

    private void addMember(User user, String nickname, BosalColor color) {
        roomMemberRepository.save(RoomMember.create(room, user, nickname, color, NOW.minusDays(1)));
    }

    private void addLeftMember(String nickname, LocalDateTime leftAt) {
        User left = userRepository.save(User.create("anon-" + nickname));
        RoomMember member = RoomMember.create(room, left, nickname, BosalColor.YELLOW, leftAt.minusDays(1));
        member.leave(leftAt, LeaveType.LEFT);
        roomMemberRepository.save(member);
    }

    private String nicknameOf(User user) {
        return roomMemberRepository.findByRoom_IdAndUser_IdAndLeftAtIsNull(room.getId(), user.getId())
                .orElseThrow()
                .getNickname();
    }

    /** 성공하면 true, 닉네임 중복에 걸리면 false. */
    private List<Boolean> updateConcurrently(ExecutorService executor, List<User> users, String nickname) {
        CountDownLatch ready = new CountDownLatch(users.size());
        CountDownLatch start = new CountDownLatch(1);
        List<CompletableFuture<Boolean>> results = users.stream()
                .map(user -> CompletableFuture.supplyAsync(() -> {
                    ready.countDown();
                    await(start);
                    try {
                        roomService.updateNickname(user.getId(), room.getId(), new RoomNicknameUpdateRequest(nickname));
                        return true;
                    } catch (BaseException e) {
                        assertThat(e.getErrorCode()).isEqualTo(BaseErrorCode.NICKNAME_DUPLICATED);
                        return false;
                    }
                }, executor))
                .toList();

        await(ready);
        start.countDown();
        return results.stream().map(CompletableFuture::join).toList();
    }

    private void await(CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }
}
