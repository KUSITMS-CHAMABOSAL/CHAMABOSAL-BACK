package com.chamabosal.domain.room.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.chamabosal.domain.room.dto.request.InviteJoinRequest;
import com.chamabosal.domain.room.entity.BosalColor;
import com.chamabosal.domain.room.entity.LeaveType;
import com.chamabosal.domain.room.entity.Room;
import com.chamabosal.domain.room.entity.RoomMember;
import com.chamabosal.domain.round.entity.Round;
import com.chamabosal.domain.round.entity.RoundResultType;
import com.chamabosal.domain.round.entity.RoundStatus;
import com.chamabosal.domain.user.entity.User;
import com.chamabosal.domain.vote.service.VoteCloseScheduler;
import com.chamabosal.global.common.exception.BaseErrorCode;
import com.chamabosal.global.common.exception.BaseException;
import java.util.List;
import java.util.Optional;
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

class InviteJoinTest extends RoomTestSupport {

    @Autowired
    VoteCloseScheduler voteCloseScheduler;

    private User userA;
    private User userB;
    private User userD;
    private Room room;
    private RoomMember memberB;

    /** 함께할 인원 4명인 방에 방장, A(`민지`, 초대한 사람), B(`Jisu`) 3명이 있다. */
    @BeforeEach
    void setUp() {
        userA = newUser();
        userB = newUser();
        userD = newUser();
        room = createRoomWithInviter(4, userA);
        memberB = addMember(room, userB, "Jisu");
    }

    @Test
    void INVITE_03_1_입장하면_멤버가_되고_남은_색_중_하나가_배정된다() throws Exception {
        List<BosalColor> usedColors = currentColors(room);

        ResultActions result = join(userD, "도윤")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.roomId").value(room.getId()))
                .andExpect(jsonPath("$.data.nickname").value("도윤"));

        RoomMember memberD = currentMemberOf(userD).orElseThrow();
        result.andExpect(jsonPath("$.data.memberId").value(memberD.getId()));
        assertThat(memberD.getNickname()).isEqualTo("도윤");
        assertThat(memberD.getJoinedAt()).isEqualTo(NOW);
        assertThat(usedColors).doesNotContain(memberD.getColor());
    }

    @Test
    void INVITE_03_2_미리보기_뒤_자리가_차면_ROOM_003을_돌려준다() throws Exception {
        mockMvc.perform(get("/api/invites/{token}", TOKEN_A).header("Authorization", bearer(userD)))
                .andExpect(jsonPath("$.data.status").value("JOINABLE"));
        addMember(room, newUser(), "하린");

        join(userD, "도윤")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ROOM_003"));

        assertThat(currentMemberOf(userD)).isEmpty();
    }

    @Test
    void INVITE_03_3_내보내진_사람은_ROOM_006을_받고_멤버가_되지_않는다() throws Exception {
        addLeftMember(room, userD, "도윤", BosalColor.MINT, NOW.minusDays(1), LeaveType.KICKED);

        join(userD, "도윤")
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ROOM_006"));

        assertThat(currentMemberOf(userD)).isEmpty();
    }

    @Test
    void INVITE_03_4_참여_방이_2개면_ROOM_001을_돌려준다() throws Exception {
        joinOtherRooms(userD, 2);

        join(userD, "도윤")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ROOM_001"));

        assertThat(currentMemberOf(userD)).isEmpty();
    }

    @Test
    void INVITE_03_5_이미_멤버면_새로_만들지_않고_기존_정보를_돌려준다() throws Exception {
        long memberCount = roomMemberRepository.count();

        join(userB, "지수")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.memberId").value(memberB.getId()))
                .andExpect(jsonPath("$.data.nickname").value("Jisu"));

        assertThat(roomMemberRepository.count()).isEqualTo(memberCount);
    }

    @Test
    void INVITE_03_6_대소문자만_다른_닉네임이면_ROOM_004를_돌려준다() throws Exception {
        join(userD, "jisu")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ROOM_004"))
                .andExpect(jsonPath("$.field").value("nickname"));

        assertThat(currentMemberOf(userD)).isEmpty();
    }

    @Test
    void INVITE_03_7_6개월_안에_다시_들어오면_같은_행을_쓴다() throws Exception {
        RoomMember previous = addLeftMember(room, userD, "지수", BosalColor.MINT, NOW.minusMonths(5), LeaveType.LEFT);

        join(userD, "지수")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.memberId").value(previous.getId()));

        RoomMember rejoined = roomMemberRepository.findById(previous.getId()).orElseThrow();
        assertThat(rejoined.isCurrent()).isTrue();
        assertThat(rejoined.getLeaveType()).isNull();
        assertThat(rejoined.getJoinedAt()).isEqualTo(NOW);
    }

    @Test
    void INVITE_03_8_다시_들어오며_닉네임을_바꿀_수_있다() throws Exception {
        RoomMember previous = addLeftMember(room, userD, "지수", BosalColor.MINT, NOW.minusMonths(5), LeaveType.LEFT);

        join(userD, "지수2")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.memberId").value(previous.getId()));

        assertThat(roomMemberRepository.findById(previous.getId()).orElseThrow().getNickname()).isEqualTo("지수2");
    }

    @Test
    void INVITE_03_9_예전_색을_다른_멤버가_쓰면_남은_색이_배정된다() throws Exception {
        BosalColor colorOfB = memberB.getColor();
        addLeftMember(room, userD, "지수", colorOfB, NOW.minusMonths(5), LeaveType.LEFT);
        List<BosalColor> usedColors = currentColors(room);

        join(userD, "지수").andExpect(status().isCreated());

        assertThat(usedColors).doesNotContain(currentMemberOf(userD).orElseThrow().getColor());
    }

    @Test
    void INVITE_03_10_예전_색을_아무도_안_쓰면_그대로_쓴다() throws Exception {
        BosalColor unused = unusedColor(room);
        addLeftMember(room, userD, "지수", unused, NOW.minusMonths(5), LeaveType.LEFT);

        join(userD, "지수").andExpect(status().isCreated());

        assertThat(currentMemberOf(userD).orElseThrow().getColor()).isEqualTo(unused);
    }

    @Test
    void INVITE_03_11_6개월이_지나_다시_들어오면_새_행이_생긴다() throws Exception {
        RoomMember previous = addLeftMember(room, userD, "지수", BosalColor.MINT, NOW.minusMonths(7), LeaveType.LEFT);

        join(userD, "지수").andExpect(status().isCreated());

        RoomMember current = currentMemberOf(userD).orElseThrow();
        assertThat(current.getId()).isNotEqualTo(previous.getId());
        RoomMember old = roomMemberRepository.findById(previous.getId()).orElseThrow();
        assertThat(old.isCurrent()).isFalse();
        assertThat(old.getLeaveType()).isEqualTo(LeaveType.LEFT);
    }

    @Test
    void INVITE_03_12_들어오기_전_결과와_한_솥_축하는_본_것으로_채운다() throws Exception {
        ReflectionTestUtils.setField(room, "potCompletedCount", 2);
        roomRepository.save(room);
        saveRound(room, NOW.toLocalDate().minusDays(3), RoundStatus.ANNOUNCED);
        saveRound(room, NOW.toLocalDate().minusDays(2), RoundStatus.ANNOUNCED);
        saveRound(room, NOW.toLocalDate().minusDays(1), RoundStatus.NO_VOTE);
        leave(memberB.getId(), NOW.minusMonths(1), LeaveType.LEFT);

        join(userD, "도윤").andExpect(status().isCreated());
        join(userB, "Jisu").andExpect(status().isCreated());

        RoomMember memberD = currentMemberOf(userD).orElseThrow();
        RoomMember rejoinedB = roomMemberRepository.findById(memberB.getId()).orElseThrow();
        assertThat(memberD.getSeenPotCount()).isEqualTo(2);
        assertThat(rejoinedB.getSeenPotCount()).isEqualTo(2);
        assertThat(memberD.getSeenResultRoundDate()).isEqualTo(NOW.toLocalDate().minusDays(2));
        assertThat(rejoinedB.getSeenResultRoundDate()).isEqualTo(NOW.toLocalDate().minusDays(2));
    }

    @Test
    void INVITE_03_13_인증_중에_들어오면_이번_회차에_인증할_수_있다() throws Exception {
        certify(userA, room).andExpect(status().isCreated());

        join(userD, "도윤").andExpect(status().isCreated());

        certify(userD, room)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.roundDate").value(NOW.toLocalDate().toString()));
    }

    @Test
    void INVITE_03_14_오늘_투표가_시작된_뒤_들어오면_명단은_그대로이고_다음_회차부터_참여한다() throws Exception {
        for (User user : List.of(ownerOf(room), userA, userB)) {
            certify(user, room).andExpect(status().isCreated());
        }
        Round today = roundRepository.findByRoom_IdAndRoundDate(room.getId(), NOW.toLocalDate()).orElseThrow();
        assertThat(today.getStatus()).isEqualTo(RoundStatus.VOTING);

        join(userD, "도윤").andExpect(status().isCreated());

        RoomMember memberD = currentMemberOf(userD).orElseThrow();
        assertThat(roundParticipantRepository.findByRound_Id(today.getId()))
                .extracting(participant -> participant.getMember().getId())
                .doesNotContain(memberD.getId())
                .hasSize(3);
        certify(userD, room)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CERT_006"));

        clock.set(today.getVoteClosesAt());
        voteCloseScheduler.closeDueVotes();
        clock.set(today.getVoteClosesAt().plusMinutes(10));
        certify(userD, room)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.roundDate").value(NOW.toLocalDate().plusDays(1).toString()));
    }

    @Test
    void INVITE_03_17_다시_들어오면_지난_기록이_내_닉네임으로_보인다() throws Exception {
        Round round = saveRound(room, NOW.toLocalDate().minusDays(2), RoundStatus.VOTING);
        round.announce(NOW.minusDays(1), RoundResultType.PICKED, memberB);
        roundRepository.save(round);
        mockMvc.perform(delete("/api/rooms/{roomId}/members/me", room.getId()).header("Authorization", bearer(userB)))
                .andExpect(status().isOk());
        viewResult(userA, round)
                .andExpect(jsonPath("$.data.picked.memberId").value(memberB.getId()))
                .andExpect(jsonPath("$.data.picked.isLeft").value(true));

        join(userB, "지수").andExpect(status().isCreated());

        viewResult(userA, round)
                .andExpect(jsonPath("$.data.picked.memberId").value(memberB.getId()))
                .andExpect(jsonPath("$.data.picked.nickname").value("지수"))
                .andExpect(jsonPath("$.data.picked.isLeft").value(false));
    }

    @Test
    void INVITE_03_15_마지막_자리에_동시에_들어오면_한_명만_멤버가_된다() {
        User userE = newUser();
        List<User> users = List.of(userD, userE);

        List<Boolean> results = joinConcurrently(users, List.of(TOKEN_A, TOKEN_A), BaseErrorCode.ROOM_FULL);

        assertThat(results).containsExactlyInAnyOrder(true, false);
        assertThat(roomMemberRepository.findByRoom_IdAndLeftAtIsNull(room.getId())).hasSize(4);
    }

    @Test
    void INVITE_03_16_두_방에_동시에_들어가도_참여_방은_2개를_넘지_않는다() {
        joinOtherRooms(userD, 1);
        Room otherRoom = createRoom(4);
        RoomMember otherInviter = RoomMember.create(otherRoom, newUser(), "서연", unusedColor(otherRoom), NOW.minusDays(1));
        otherInviter.issueInviteToken("token-other");
        roomMemberRepository.save(otherInviter);

        List<Boolean> results = joinConcurrently(List.of(userD, userD), List.of(TOKEN_A, "token-other"),
                BaseErrorCode.ROOM_LIMIT_EXCEEDED);

        assertThat(results).containsExactlyInAnyOrder(true, false);
        assertThat(roomMemberRepository.countByUser_IdAndLeftAtIsNullAndRoom_DeletedAtIsNull(userD.getId()))
                .isEqualTo(2);
    }

    @Test
    void INVITE_03_18_나간_멤버의_닉네임으로_들어올_수_있다() throws Exception {
        addLeftMember(room, newUser(), "하린", BosalColor.MINT, NOW.minusMonths(1), LeaveType.LEFT);

        join(userD, "하린").andExpect(status().isCreated()).andExpect(jsonPath("$.data.nickname").value("하린"));
    }

    @Test
    void INVITE_03_19_다시_들어올_때_예전_닉네임을_다른_멤버가_쓰면_ROOM_004다() throws Exception {
        // D가 jisu로 나간 뒤 B가 Jisu로 들어와 있다 (나간 멤버의 닉네임은 다른 사람이 쓸 수 있다).
        RoomMember previous = addLeftMember(room, userD, "jisu", BosalColor.MINT, NOW.minusMonths(1), LeaveType.LEFT);

        join(userD, "jisu")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ROOM_004"))
                .andExpect(jsonPath("$.field").value("nickname"));

        assertThat(roomMemberRepository.findById(previous.getId()).orElseThrow().isCurrent()).isFalse();
    }

    @Test
    void INVITE_03_20_닉네임_앞뒤_띄어쓰기는_지우고_저장한다() throws Exception {
        join(userD, " 도윤 ").andExpect(status().isCreated()).andExpect(jsonPath("$.data.nickname").value("도윤"));
    }

    private ResultActions join(User user, String nickname) throws Exception {
        return mockMvc.perform(post("/api/invites/{token}/join", TOKEN_A)
                .header("Authorization", bearer(user))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"nickname\":\"%s\"}".formatted(nickname)));
    }

    private User ownerOf(Room room) {
        return roomMemberRepository.findById(room.getOwnerMemberId()).orElseThrow().getUser();
    }

    private ResultActions viewResult(User user, Round round) throws Exception {
        return mockMvc.perform(get("/api/rounds/{roundId}/result", round.getId()).header("Authorization", bearer(user)))
                .andExpect(status().isOk());
    }

    private Optional<RoomMember> currentMemberOf(User user) {
        return roomMemberRepository.findByRoom_IdAndUser_IdAndLeftAtIsNull(room.getId(), user.getId());
    }

    /** 성공하면 true, expected 에러에 걸리면 false. 닉네임은 요청마다 다르게 보낸다. */
    private List<Boolean> joinConcurrently(List<User> users, List<String> tokens, BaseErrorCode expected) {
        ExecutorService executor = Executors.newFixedThreadPool(users.size());
        try {
            CountDownLatch ready = new CountDownLatch(users.size());
            CountDownLatch start = new CountDownLatch(1);
            List<CompletableFuture<Boolean>> futures = IntStream.range(0, users.size())
                    .mapToObj(i -> CompletableFuture.supplyAsync(() -> {
                        ready.countDown();
                        await(start);
                        try {
                            inviteService.join(users.get(i).getId(), tokens.get(i), new InviteJoinRequest("동시" + i));
                            return true;
                        } catch (BaseException e) {
                            assertThat(e.getErrorCode()).isEqualTo(expected);
                            return false;
                        }
                    }, executor))
                    .toList();
            await(ready);
            start.countDown();
            return futures.stream().map(CompletableFuture::join).toList();
        } finally {
            executor.shutdown();
        }
    }
}
