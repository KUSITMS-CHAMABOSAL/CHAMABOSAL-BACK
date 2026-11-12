package com.chamabosal.domain.room.controller;

import com.chamabosal.domain.room.dto.request.RoomCreateRequest;
import com.chamabosal.domain.room.entity.BosalColor;
import com.chamabosal.domain.room.entity.LeaveType;
import com.chamabosal.domain.room.entity.Room;
import com.chamabosal.domain.room.entity.RoomGoal;
import com.chamabosal.domain.room.entity.RoomMember;
import com.chamabosal.domain.room.repository.RoomMemberRepository;
import com.chamabosal.domain.room.repository.RoomRepository;
import com.chamabosal.domain.room.service.InviteService;
import com.chamabosal.domain.room.service.RoomService;
import com.chamabosal.domain.user.entity.User;
import com.chamabosal.domain.user.repository.UserRepository;
import com.chamabosal.global.config.ClockConfig;
import com.chamabosal.global.security.jwt.JwtProvider;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZonedDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.stream.IntStream;
import org.junit.jupiter.api.AfterEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;

/** 초대 시나리오 테스트가 함께 쓰는 방·멤버 준비 코드. 방장이 만든 방에 A(`민지`)가 초대 토큰을 가진 멤버로 있다. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(InviteTestSupport.FixedClockConfig.class)
abstract class InviteTestSupport {

    static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 11, 12, 0);
    static final String TOKEN_A = "token-a";

    @TestConfiguration
    static class FixedClockConfig {

        @Bean
        @Primary
        Clock fixedClock() {
            return Clock.fixed(ZonedDateTime.of(NOW, ClockConfig.KST).toInstant(), ClockConfig.KST);
        }
    }

    @Autowired
    MockMvc mockMvc;

    @Autowired
    RoomService roomService;

    @Autowired
    InviteService inviteService;

    @Autowired
    RoomRepository roomRepository;

    @Autowired
    RoomMemberRepository roomMemberRepository;

    @Autowired
    UserRepository userRepository;

    @Autowired
    JwtProvider jwtProvider;

    private int userSequence;

    @AfterEach
    void tearDown() {
        roomMemberRepository.deleteAll();
        roomRepository.deleteAll();
        userRepository.deleteAll();
    }

    User newUser() {
        return userRepository.save(User.create("anon-" + userSequence++));
    }

    /** 방장이 방을 만들고, 초대 토큰 TOKEN_A를 가진 A(`민지`)를 멤버로 넣는다. */
    Room createRoomWithInviter(int memberLimit, User inviter) {
        Room room = createRoom(memberLimit);
        RoomMember member = RoomMember.create(room, inviter, "민지", unusedColor(room), NOW.minusDays(1));
        member.issueInviteToken(TOKEN_A);
        roomMemberRepository.save(member);
        return room;
    }

    Room createRoom(int memberLimit) {
        Long roomId = roomService.create(newUser().getId(),
                new RoomCreateRequest("배달 끊기 방", RoomGoal.DELIVERY, memberLimit, "방장")).roomId();
        return roomRepository.findById(roomId).orElseThrow();
    }

    RoomMember addMember(Room room, User user, String nickname) {
        return roomMemberRepository.save(RoomMember.create(room, user, nickname, unusedColor(room), NOW.minusDays(1)));
    }

    RoomMember addLeftMember(Room room, User user, String nickname, BosalColor color, LocalDateTime leftAt,
                             LeaveType leaveType) {
        RoomMember member = RoomMember.create(room, user, nickname, color, leftAt.minusDays(1));
        member.leave(leftAt, leaveType);
        return roomMemberRepository.save(member);
    }

    void leave(Long memberId, LocalDateTime leftAt, LeaveType leaveType) {
        RoomMember member = roomMemberRepository.findById(memberId).orElseThrow();
        member.leave(leftAt, leaveType);
        roomMemberRepository.save(member);
    }

    /** user를 다른 방 count개에 참여시킨다. */
    void joinOtherRooms(User user, int count) {
        IntStream.range(0, count).forEach(i -> addMember(createRoom(4), user, "다른방" + i));
    }

    void deleteRoom(Room room) {
        ReflectionTestUtils.setField(room, "deletedAt", NOW.minusDays(1));
        roomRepository.save(room);
    }

    BosalColor unusedColor(Room room) {
        List<BosalColor> used = currentColors(room);
        return Arrays.stream(BosalColor.values()).filter(color -> !used.contains(color)).findFirst().orElseThrow();
    }

    List<BosalColor> currentColors(Room room) {
        return roomMemberRepository.findByRoom_IdAndLeftAtIsNull(room.getId()).stream()
                .map(RoomMember::getColor)
                .toList();
    }

    String bearer(User user) {
        return "Bearer " + jwtProvider.createAccessToken(user.getId());
    }

    static void await(CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }
}
