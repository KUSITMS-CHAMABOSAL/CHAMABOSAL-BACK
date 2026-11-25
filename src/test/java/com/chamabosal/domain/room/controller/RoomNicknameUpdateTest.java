package com.chamabosal.domain.room.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.chamabosal.domain.pot.repository.PotContributionRepository;
import com.chamabosal.domain.pot.repository.RoomPotRepository;
import com.chamabosal.domain.room.dto.request.RoomCreateRequest;
import com.chamabosal.domain.room.entity.BosalColor;
import com.chamabosal.domain.room.entity.Room;
import com.chamabosal.domain.room.entity.RoomGoal;
import com.chamabosal.domain.room.entity.RoomMember;
import com.chamabosal.domain.room.repository.RoomMemberRepository;
import com.chamabosal.domain.room.repository.RoomRepository;
import com.chamabosal.domain.room.service.RoomService;
import com.chamabosal.domain.round.entity.Round;
import com.chamabosal.domain.round.entity.RoundStatus;
import com.chamabosal.domain.round.repository.RoundRepository;
import com.chamabosal.domain.user.entity.User;
import com.chamabosal.domain.user.repository.UserRepository;
import com.chamabosal.global.config.ClockConfig;
import com.chamabosal.global.security.jwt.JwtProvider;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZonedDateTime;
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
import org.springframework.test.util.ReflectionTestUtils;

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
    private PotContributionRepository potContributionRepository;

    @Autowired
    private RoomPotRepository roomPotRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoundRepository roundRepository;

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
                new RoomCreateRequest("배달 끊기 방", RoomGoal.DELIVERY, 4, 300, "민지", BosalColor.PINK)).roomId();
        room = roomRepository.findById(roomId).orElseThrow();
    }

    @AfterEach
    void tearDown() {
        roundRepository.deleteAll();
        potContributionRepository.deleteAll();
        roomPotRepository.deleteAll();
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
    void ROOM_04_2_현재_멤버와_같은_닉네임도_쓸_수_있다() throws Exception {
        addMember(userB, "지수", BosalColor.MINT);

        update(userA, "지수")
                .andExpect(status().isOk());

        assertThat(nicknameOf(userA)).isEqualTo("지수");
    }

    @Test
    void ROOM_04_4_앞뒤_띄어쓰기는_지우고_바꾼다() throws Exception {
        update(userA, " 지수 ")
                .andExpect(status().isOk());

        assertThat(nicknameOf(userA)).isEqualTo("지수");
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
    void ROOM_04_7_투표_중이면_ROOM_012를_돌려주고_닉네임은_그대로다() throws Exception {
        saveRound(NOW.toLocalDate(), RoundStatus.VOTING);

        update(userA, "민지2")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ROOM_012"));

        assertThat(nicknameOf(userA)).isEqualTo("민지");
    }

    @Test
    void ROOM_04_8_어제_회차가_투표_중이면_ROOM_012를_돌려준다() throws Exception {
        saveRound(NOW.toLocalDate().minusDays(1), RoundStatus.VOTING);

        update(userA, "민지2")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ROOM_012"));

        assertThat(nicknameOf(userA)).isEqualTo("민지");
    }

    @Test
    void 인증이_마감됐는데_투표가_아직_안_열린_회차가_있으면_ROOM_012를_돌려준다() throws Exception {
        saveRound(NOW.toLocalDate().minusDays(1), RoundStatus.CERTIFYING);

        update(userA, "민지2")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ROOM_012"));

        assertThat(nicknameOf(userA)).isEqualTo("민지");
    }

    @Test
    void ROOM_04_9_투표_중이어도_지금과_같은_닉네임이면_성공한다() throws Exception {
        saveRound(NOW.toLocalDate(), RoundStatus.VOTING);

        update(userA, "민지")
                .andExpect(status().isOk());

        assertThat(nicknameOf(userA)).isEqualTo("민지");
    }

    private void saveRound(LocalDate roundDate, RoundStatus status) {
        Round round = Round.create(room, roundDate);
        ReflectionTestUtils.setField(round, "status", status);
        roundRepository.save(round);
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

    private String nicknameOf(User user) {
        return roomMemberRepository.findByRoom_IdAndUser_IdAndLeftAtIsNull(room.getId(), user.getId())
                .orElseThrow()
                .getNickname();
    }
}
