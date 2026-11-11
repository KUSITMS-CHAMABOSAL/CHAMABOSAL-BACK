package com.chamabosal.domain.room.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.chamabosal.domain.room.dto.request.RoomCreateRequest;
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
import com.chamabosal.global.security.jwt.JwtProvider;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.IntStream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

@SpringBootTest
@AutoConfigureMockMvc
class RoomCreateTest {

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

    private User user;

    @BeforeEach
    void setUp() {
        user = userRepository.save(User.create("anon-a"));
    }

    @AfterEach
    void tearDown() {
        roomMemberRepository.deleteAll();
        roomRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    void ROOM_01_1_방을_만들고_방장_멤버를_등록한다() throws Exception {
        joinRooms(1);

        create("배달 끊기 방", 4, "민지")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.roomId").isNumber())
                .andExpect(jsonPath("$.data.memberId").isNumber())
                .andExpect(jsonPath("$.data.nickname").value("민지"))
                .andExpect(jsonPath("$.data.color").isNotEmpty());

        RoomMember owner = roomMemberRepository.findAll().stream()
                .filter(member -> member.getNickname().equals("민지"))
                .findFirst().orElseThrow();
        Room room = roomRepository.findById(owner.getRoom().getId()).orElseThrow();
        assertThat(room.getOwnerMemberId()).isEqualTo(owner.getId());
        assertThat(room.getMemberLimit()).isEqualTo(4);
        assertThat(owner.getUser().getId()).isEqualTo(user.getId());
        assertThat(owner.getColor()).isNotNull();
        assertThat(owner.getLeftAt()).isNull();
    }

    @Test
    void ROOM_01_2_참여_방이_2개면_만들지_않는다() throws Exception {
        joinRooms(2);

        create("배달 끊기 방", 4, "민지")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ROOM_001"));

        assertThat(roomRepository.count()).isEqualTo(2);
    }

    @Test
    void ROOM_01_3_방_이름_앞뒤_띄어쓰기는_지우고_저장한다() throws Exception {
        create(" 배달 끊기 ", 4, "민지")
                .andExpect(status().isCreated());

        assertThat(roomRepository.findAll()).extracting(Room::getName).containsExactly("배달 끊기");
    }

    @Test
    void ROOM_01_4_띄어쓰기만_입력하면_입력값_오류다() throws Exception {
        create("   ", 4, "민지")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON_002"))
                .andExpect(jsonPath("$.field").value("name"));

        assertThat(roomRepository.count()).isZero();
    }

    @Test
    void ROOM_01_5_이모지나_자모_단독_닉네임은_입력값_오류다() throws Exception {
        create("배달 끊기 방", 4, "민지😀")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON_002"))
                .andExpect(jsonPath("$.field").value("nickname"));
        create("배달 끊기 방", 4, "ㅁㅈ")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON_002"))
                .andExpect(jsonPath("$.field").value("nickname"));

        assertThat(roomRepository.count()).isZero();
    }

    @Test
    void ROOM_01_7_동시에_만들어도_참여_방은_2개를_넘지_않는다() {
        joinRooms(1);
        int requestCount = 2;
        ExecutorService executor = Executors.newFixedThreadPool(requestCount);
        try {
            List<Boolean> results = createConcurrently(executor, requestCount);

            assertThat(results).containsExactlyInAnyOrder(true, false);
            assertThat(roomMemberRepository.countByUser_IdAndLeftAtIsNullAndRoom_DeletedAtIsNull(user.getId()))
                    .isEqualTo(2);
        } finally {
            executor.shutdown();
        }
    }

    @Test
    void ROOM_01_8_함께할_인원이_2에서_4명이_아니면_입력값_오류다() throws Exception {
        create("배달 끊기 방", 1, "민지")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON_002"))
                .andExpect(jsonPath("$.field").value("memberLimit"));
        create("배달 끊기 방", 5, "민지")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON_002"))
                .andExpect(jsonPath("$.field").value("memberLimit"));

        assertThat(roomRepository.count()).isZero();
    }

    private ResultActions create(String name, int memberLimit, String nickname) throws Exception {
        String body = """
                {"name":"%s","goal":"DELIVERY","memberLimit":%d,"nickname":"%s"}
                """.formatted(name, memberLimit, nickname);
        return mockMvc.perform(post("/api/rooms")
                .header("Authorization", "Bearer " + jwtProvider.createAccessToken(user.getId()))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    private void joinRooms(int count) {
        IntStream.range(0, count).forEach(i -> roomService.create(user.getId(),
                new RoomCreateRequest("기존 방" + i, RoomGoal.CAFE, 4, "기존" + i)));
    }

    /** 성공하면 true, 참여 방 2개 제한에 걸리면 false. */
    private List<Boolean> createConcurrently(ExecutorService executor, int requestCount) {
        CountDownLatch ready = new CountDownLatch(requestCount);
        CountDownLatch start = new CountDownLatch(1);
        List<CompletableFuture<Boolean>> results = IntStream.range(0, requestCount)
                .mapToObj(i -> CompletableFuture.supplyAsync(() -> {
                    ready.countDown();
                    await(start);
                    try {
                        roomService.create(user.getId(), new RoomCreateRequest("새 방" + i, RoomGoal.ETC, 4, "새방" + i));
                        return true;
                    } catch (BaseException e) {
                        assertThat(e.getErrorCode()).isEqualTo(BaseErrorCode.ROOM_LIMIT_EXCEEDED);
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
