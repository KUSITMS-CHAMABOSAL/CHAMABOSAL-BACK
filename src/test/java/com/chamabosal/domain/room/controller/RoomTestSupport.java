package com.chamabosal.domain.room.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.chamabosal.domain.certification.repository.CertificationRepository;
import com.chamabosal.domain.pot.repository.PotContributionRepository;
import com.chamabosal.domain.pot.repository.RoomPotRepository;
import com.chamabosal.domain.rice.repository.RiceHistoryRepository;
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
import com.chamabosal.domain.round.entity.Round;
import com.chamabosal.domain.round.entity.RoundStatus;
import com.chamabosal.domain.round.repository.RoundParticipantRepository;
import com.chamabosal.domain.round.repository.RoundRepository;
import com.chamabosal.domain.user.entity.User;
import com.chamabosal.domain.user.repository.UserRepository;
import com.chamabosal.domain.vote.repository.VoteRepository;
import com.chamabosal.global.security.jwt.JwtProvider;
import com.chamabosal.support.MutableClock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.stream.IntStream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/** 방·초대 시나리오 테스트가 함께 쓰는 방·멤버 준비 코드. 시나리오마다 Clock을 NOW로 맞추고, 시각이 흐르는 시나리오만 clock.set으로 옮긴다. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(MutableClock.Config.class)
abstract class RoomTestSupport {

    static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 11, 12, 0);
    static final String TOKEN_A = "token-a";

    @Autowired
    MutableClock clock;

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
    PotContributionRepository potContributionRepository;

    @Autowired
    RoomPotRepository roomPotRepository;

    @Autowired
    UserRepository userRepository;

    @Autowired
    JwtProvider jwtProvider;

    private int userSequence;

    @Autowired
    RoundRepository roundRepository;

    @Autowired
    RoundParticipantRepository roundParticipantRepository;

    @Autowired
    CertificationRepository certificationRepository;

    @Autowired
    VoteRepository voteRepository;

    @Autowired
    RiceHistoryRepository riceHistoryRepository;

    @BeforeEach
    void resetClock() {
        clock.set(NOW);
    }

    @AfterEach
    void tearDown() {
        riceHistoryRepository.deleteAll();
        voteRepository.deleteAll();
        roundParticipantRepository.deleteAll();
        certificationRepository.deleteAll();
        roundRepository.deleteAll();
        potContributionRepository.deleteAll();
        roomPotRepository.deleteAll();
        roomMemberRepository.deleteAll();
        roomRepository.deleteAll();
        userRepository.deleteAll();
    }

    User newUser() {
        return userRepository.save(User.create("anon-" + userSequence++));
    }

    /** 방장이 방을 만들고, 초대 토큰 TOKEN_A를 가진 A(`민지`)를 멤버로 넣는다. */
    Room createRoomWithInviter(int memberLimit, User inviter) {
        return createRoomWithInviter(memberLimit, inviter, RoomGoal.DELIVERY);
    }

    Room createRoomWithInviter(int memberLimit, User inviter, RoomGoal goal) {
        Room room = createRoom(memberLimit, goal);
        RoomMember member = RoomMember.create(room, inviter, "민지", unusedColor(room), NOW.minusDays(1));
        member.issueInviteToken(TOKEN_A);
        roomMemberRepository.save(member);
        return room;
    }

    Room createRoom(int memberLimit) {
        return createRoom(memberLimit, RoomGoal.DELIVERY);
    }

    Room createRoom(int memberLimit, RoomGoal goal) {
        Long roomId = roomService.create(newUser().getId(),
                new RoomCreateRequest("배달 끊기 방", goal, memberLimit, 300, "방장", BosalColor.PINK)).roomId();
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

    /** 회차 상태 전이는 투표 기능에서 만들어서, 지금은 상태를 바로 넣는다. */
    Round saveRound(Room room, LocalDate roundDate, RoundStatus status) {
        Round round = Round.create(room, roundDate);
        ReflectionTestUtils.setField(round, "status", status);
        return roundRepository.save(round);
    }

    /** 인증 API로 `참았어요`를 등록한다. 회차는 Clock 시각으로 정해진다. */
    ResultActions certify(User user, Room room) throws Exception {
        return mockMvc.perform(post("/api/rooms/{roomId}/certifications", room.getId())
                .header("Authorization", bearer(user))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"result\":\"ENDURED\",\"memo\":\"집밥 해먹었어요\"}"));
    }

    void deleteRoom(Room room) {
        room.delete(NOW.minusDays(1));
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
