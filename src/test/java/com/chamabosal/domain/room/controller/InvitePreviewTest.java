package com.chamabosal.domain.room.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.chamabosal.domain.room.entity.BosalColor;
import com.chamabosal.domain.room.entity.LeaveType;
import com.chamabosal.domain.room.entity.Room;
import com.chamabosal.domain.room.entity.RoomMember;
import com.chamabosal.domain.user.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.ResultActions;

class InvitePreviewTest extends InviteTestSupport {

    private User userA;
    private User userB;
    private User userD;
    private RoomMember memberA;

    @BeforeEach
    void setUp() {
        userA = newUser();
        userB = newUser();
        userD = newUser();
    }

    @Test
    void INVITE_02_1_입장할_수_있으면_방_정보와_초대한_사람_닉네임을_돌려준다() throws Exception {
        Room room = roomOfThree(4);
        joinOtherRooms(userD, 1);

        preview(userD, TOKEN_A)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("JOINABLE"))
                .andExpect(jsonPath("$.data.room.roomId").value(room.getId()))
                .andExpect(jsonPath("$.data.room.name").value("배달 끊기 방"))
                .andExpect(jsonPath("$.data.room.goal").value("DELIVERY"))
                .andExpect(jsonPath("$.data.room.memberCount").value(3))
                .andExpect(jsonPath("$.data.room.memberLimit").value(4))
                .andExpect(jsonPath("$.data.inviterNickname").value("민지"))
                .andExpect(jsonPath("$.data.previousNickname").isEmpty());
    }

    @Test
    void INVITE_02_2_삭제된_방이면_UNAVAILABLE이고_방_정보는_비어_있다() throws Exception {
        Room room = roomOfThree(4);
        deleteRoom(room);

        preview(userD, TOKEN_A)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("UNAVAILABLE"))
                .andExpect(jsonPath("$.data.room").isEmpty())
                .andExpect(jsonPath("$.data.inviterNickname").isEmpty());
    }

    @Test
    void INVITE_02_3_없는_토큰이면_UNAVAILABLE이다() throws Exception {
        roomOfThree(4);

        preview(userD, "no-such-token")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("UNAVAILABLE"))
                .andExpect(jsonPath("$.data.room").isEmpty());
    }

    @Test
    void INVITE_02_4_초대한_사람이_내보내졌으면_UNAVAILABLE이다() throws Exception {
        roomOfThree(4);
        leave(memberA.getId(), NOW.minusDays(1), LeaveType.KICKED);

        preview(userD, TOKEN_A)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("UNAVAILABLE"))
                .andExpect(jsonPath("$.data.inviterNickname").isEmpty());
    }

    @Test
    void INVITE_02_5_초대한_사람이_스스로_나갔으면_JOINABLE이고_닉네임은_비어_있다() throws Exception {
        roomOfThree(4);
        leave(memberA.getId(), NOW.minusDays(1), LeaveType.LEFT);

        preview(userD, TOKEN_A)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("JOINABLE"))
                .andExpect(jsonPath("$.data.room.memberCount").value(2))
                .andExpect(jsonPath("$.data.inviterNickname").isEmpty());
    }

    @Test
    void INVITE_02_6_이미_멤버면_ALREADY_MEMBER다() throws Exception {
        Room room = roomOfThree(4);

        preview(userB, TOKEN_A)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("ALREADY_MEMBER"))
                .andExpect(jsonPath("$.data.room.roomId").value(room.getId()));
    }

    @Test
    void INVITE_02_7_내가_내보내진_방이면_UNAVAILABLE이다() throws Exception {
        Room room = roomOfThree(4);
        addLeftMember(room, userD, "도윤", BosalColor.MINT, NOW.minusDays(1), LeaveType.KICKED);

        preview(userD, TOKEN_A)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("UNAVAILABLE"))
                .andExpect(jsonPath("$.data.room").isEmpty())
                .andExpect(jsonPath("$.data.inviterNickname").isEmpty())
                .andExpect(jsonPath("$.data.previousNickname").isEmpty());
    }

    @Test
    void INVITE_02_8_참여_방이_2개면_방이_가득_차도_ROOM_LIMIT이다() throws Exception {
        Room room = roomOfThree(4);
        addMember(room, newUser(), "하린");
        joinOtherRooms(userD, 2);

        preview(userD, TOKEN_A)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("ROOM_LIMIT"));
    }

    @Test
    void INVITE_02_9_방이_가득_차면_ROOM_FULL이다() throws Exception {
        Room room = roomOfThree(4);
        addMember(room, newUser(), "하린");
        joinOtherRooms(userD, 1);

        preview(userD, TOKEN_A)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("ROOM_FULL"));
    }

    @Test
    void INVITE_02_10_자리가_나면_같은_링크로_JOINABLE이다() throws Exception {
        Room room = roomOfThree(4);
        RoomMember memberC = addMember(room, newUser(), "하린");
        preview(userD, TOKEN_A).andExpect(jsonPath("$.data.status").value("ROOM_FULL"));

        leave(memberC.getId(), NOW.minusMinutes(1), LeaveType.LEFT);

        preview(userD, TOKEN_A)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("JOINABLE"));
    }

    @Test
    void INVITE_02_11_나간_지_6개월이_안_됐으면_예전_닉네임을_돌려준다() throws Exception {
        Room room = roomOfThree(4);
        addLeftMember(room, userD, "지수", BosalColor.MINT, NOW.minusMonths(5), LeaveType.LEFT);

        preview(userD, TOKEN_A)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("JOINABLE"))
                .andExpect(jsonPath("$.data.previousNickname").value("지수"));
    }

    @Test
    void INVITE_02_12_나간_지_6개월이_지났으면_예전_닉네임은_비어_있다() throws Exception {
        Room room = roomOfThree(4);
        addLeftMember(room, userD, "지수", BosalColor.MINT, NOW.minusMonths(7), LeaveType.LEFT);

        preview(userD, TOKEN_A)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("JOINABLE"))
                .andExpect(jsonPath("$.data.previousNickname").isEmpty());
    }

    @Test
    void INVITE_02_13_함께할_인원이_3명인_방에_3명이면_ROOM_FULL이다() throws Exception {
        roomOfThree(3);
        joinOtherRooms(userD, 1);

        preview(userD, TOKEN_A)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("ROOM_FULL"));
    }

    /** 방장, A(`민지`, 초대한 사람), B(`지수2`) 3명이 있는 방. */
    private Room roomOfThree(int memberLimit) {
        Room room = createRoomWithInviter(memberLimit, userA);
        memberA = roomMemberRepository.findByInviteToken(TOKEN_A).orElseThrow();
        addMember(room, userB, "지수2");
        return room;
    }

    private ResultActions preview(User user, String token) throws Exception {
        return mockMvc.perform(get("/api/invites/{token}", token)
                .header("Authorization", bearer(user)));
    }
}
