package com.chamabosal.domain.user.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.chamabosal.domain.pot.repository.PotContributionRepository;
import com.chamabosal.domain.pot.repository.RoomPotRepository;
import com.chamabosal.domain.room.entity.BosalColor;
import com.chamabosal.domain.room.entity.LeaveType;
import com.chamabosal.domain.room.entity.Room;
import com.chamabosal.domain.room.entity.RoomGoal;
import com.chamabosal.domain.room.entity.RoomMember;
import com.chamabosal.domain.room.repository.RoomMemberRepository;
import com.chamabosal.domain.room.repository.RoomRepository;
import com.chamabosal.domain.round.entity.Round;
import com.chamabosal.domain.round.entity.RoundStatus;
import com.chamabosal.domain.round.repository.RoundRepository;
import com.chamabosal.domain.user.entity.User;
import com.chamabosal.domain.user.repository.PushAgreementRepository;
import com.chamabosal.domain.user.repository.UserRepository;
import com.chamabosal.global.security.jwt.JwtProvider;
import com.chamabosal.support.MutableClock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

@SpringBootTest
@AutoConfigureMockMvc
@Import(MutableClock.Config.class)
class UserMeTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 20, 12, 0);

    @Autowired
    MockMvc mockMvc;

    @Autowired
    MutableClock clock;

    @Autowired
    JwtProvider jwtProvider;

    @Autowired
    UserRepository userRepository;

    @Autowired
    PushAgreementRepository pushAgreementRepository;

    @Autowired
    RoomRepository roomRepository;

    @Autowired
    RoomMemberRepository roomMemberRepository;

    @Autowired
    PotContributionRepository potContributionRepository;

    @Autowired
    RoomPotRepository roomPotRepository;

    @Autowired
    RoundRepository roundRepository;

    private User userA;

    @BeforeEach
    void setUp() {
        clock.set(NOW);
        userA = userRepository.save(User.create("anon-a"));
    }

    @AfterEach
    void tearDown() {
        pushAgreementRepository.deleteAll();
        roundRepository.deleteAll();
        potContributionRepository.deleteAll();
        roomPotRepository.deleteAll();
        roomMemberRepository.deleteAll();
        roomRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    void USER_01_1_참여_중인_방이_없으면_빈_목록이고_새_방을_만들_수_있다() throws Exception {
        me()
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.rooms.length()").value(0))
                .andExpect(jsonPath("$.data.canCreateRoom").value(true))
                .andExpect(jsonPath("$.data.isPokePushEnabled").value(true))
                .andExpect(jsonPath("$.data.isActivityPushEnabled").value(true))
                .andExpect(jsonPath("$.data.equippedItemCodes.length()").value(0))
                .andExpect(jsonPath("$.data.userId").doesNotExist());
    }

    @Test
    void USER_01_2_방이_2개면_두_방을_돌려주고_새_방을_만들_수_없다() throws Exception {
        RoomMember member1 = join(newRoom("배달 끊기 방"), "민지", BosalColor.PINK, NOW.minusDays(2));
        RoomMember member2 = join(newRoom("카페 참기 방"), "Minji", BosalColor.MINT, NOW.minusDays(1));

        me()
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.canCreateRoom").value(false))
                .andExpect(jsonPath("$.data.rooms.length()").value(2))
                .andExpect(jsonPath("$.data.rooms[0].roomId").value(member1.getRoom().getId()))
                .andExpect(jsonPath("$.data.rooms[0].name").value("배달 끊기 방"))
                .andExpect(jsonPath("$.data.rooms[0].nickname").value("민지"))
                .andExpect(jsonPath("$.data.rooms[0].color").value("PINK"))
                .andExpect(jsonPath("$.data.rooms[0].hasUnseen").value(false))
                .andExpect(jsonPath("$.data.rooms[1].roomId").value(member2.getRoom().getId()))
                .andExpect(jsonPath("$.data.rooms[1].nickname").value("Minji"));
    }

    @Test
    void USER_01_3_나간_방은_목록에서_빠진다() throws Exception {
        RoomMember left = join(newRoom("배달 끊기 방"), "민지", BosalColor.PINK, NOW.minusDays(2));
        left.leave(NOW.minusDays(1), LeaveType.LEFT);
        roomMemberRepository.save(left);
        RoomMember current = join(newRoom("카페 참기 방"), "민지", BosalColor.PINK, NOW.minusDays(1));

        me()
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.rooms.length()").value(1))
                .andExpect(jsonPath("$.data.rooms[0].roomId").value(current.getRoom().getId()))
                .andExpect(jsonPath("$.data.canCreateRoom").value(true));
    }

    @Test
    void USER_01_4_최근_발표_결과를_안_봤으면_안_본_항목이_있다() throws Exception {
        RoomMember member = join(newRoom("배달 끊기 방"), "민지", BosalColor.PINK, NOW.minusDays(5));
        member.fillSeenResult(LocalDate.of(2026, 11, 17));
        roomMemberRepository.save(member);
        announced(member.getRoom(), LocalDate.of(2026, 11, 18));

        me()
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.rooms[0].hasUnseen").value(true));
    }

    @Test
    void 최근_발표_결과를_봤으면_안_본_항목이_없다() throws Exception {
        RoomMember member = join(newRoom("배달 끊기 방"), "민지", BosalColor.PINK, NOW.minusDays(5));
        member.fillSeenResult(LocalDate.of(2026, 11, 18));
        roomMemberRepository.save(member);
        announced(member.getRoom(), LocalDate.of(2026, 11, 18));

        me()
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.rooms[0].hasUnseen").value(false));
    }

    @Test
    void 한_솥_축하를_안_봤으면_안_본_항목이_있다() throws Exception {
        Room room = newRoom("배달 끊기 방");
        join(room, "민지", BosalColor.PINK, NOW.minusDays(5));
        ReflectionTestUtils.setField(room, "potCompletedCount", 1);
        roomRepository.save(room);

        me()
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.rooms[0].hasUnseen").value(true));
    }

    @Test
    void 삭제된_방은_목록에서_빠진다() throws Exception {
        Room room = newRoom("배달 끊기 방");
        join(room, "민지", BosalColor.PINK, NOW.minusDays(5));
        room.delete(NOW.minusDays(1));
        roomRepository.save(room);

        me()
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.rooms.length()").value(0));
    }

    @Test
    void 결과를_한_번도_안_봤으면_안_본_항목이_있다() throws Exception {
        RoomMember member = join(newRoom("배달 끊기 방"), "민지", BosalColor.PINK, NOW.minusDays(5));
        announced(member.getRoom(), LocalDate.of(2026, 11, 18));

        me()
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.rooms[0].hasUnseen").value(true));
    }

    @Test
    void 한_솥_축하를_봤으면_안_본_항목이_없다() throws Exception {
        Room room = newRoom("배달 끊기 방");
        ReflectionTestUtils.setField(room, "potCompletedCount", 1);
        roomRepository.save(room);
        join(room, "민지", BosalColor.PINK, NOW.minusDays(5));

        me()
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.rooms[0].hasUnseen").value(false));
    }

    private Room newRoom(String name) {
        return roomRepository.save(Room.create(name, RoomGoal.DELIVERY, 4, 300));
    }

    private RoomMember join(Room room, String nickname, BosalColor color, LocalDateTime joinedAt) {
        return roomMemberRepository.save(RoomMember.create(room, userA, nickname, color, joinedAt));
    }

    private void announced(Room room, LocalDate roundDate) {
        Round round = Round.create(room, roundDate);
        ReflectionTestUtils.setField(round, "status", RoundStatus.ANNOUNCED);
        roundRepository.save(round);
    }

    private ResultActions me() throws Exception {
        return mockMvc.perform(get("/api/users/me").header("Authorization", bearer()));
    }

    private String bearer() {
        return "Bearer " + jwtProvider.createAccessToken(userA.getId());
    }
}
