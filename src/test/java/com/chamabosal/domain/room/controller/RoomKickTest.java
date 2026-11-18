package com.chamabosal.domain.room.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.chamabosal.domain.certification.dto.request.CertificationCreateRequest;
import com.chamabosal.domain.certification.entity.Certification;
import com.chamabosal.domain.certification.entity.CertificationResult;
import com.chamabosal.domain.certification.photo.PreparedPhoto;
import com.chamabosal.domain.certification.service.CertificationService;
import com.chamabosal.domain.room.dto.request.RoomCreateRequest;
import com.chamabosal.domain.room.entity.BosalColor;
import com.chamabosal.domain.room.entity.LeaveType;
import com.chamabosal.domain.room.entity.Room;
import com.chamabosal.domain.room.entity.RoomGoal;
import com.chamabosal.domain.room.entity.RoomMember;
import com.chamabosal.domain.round.entity.Round;
import com.chamabosal.domain.round.entity.RoundResultType;
import com.chamabosal.domain.round.entity.RoundStatus;
import com.chamabosal.domain.user.entity.User;
import com.chamabosal.global.common.exception.BaseErrorCode;
import com.chamabosal.global.common.exception.BaseException;
import com.chamabosal.global.common.exception.ErrorCode;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.ResultActions;

class RoomKickTest extends RoomTestSupport {

    @Autowired
    CertificationService certificationService;

    private User userA;
    private User userB;
    private Room room;
    private RoomMember memberA;
    private RoomMember memberB;

    /** A가 방장이고 B가 멤버인 방. */
    @BeforeEach
    void setUp() {
        userA = newUser();
        userB = newUser();
        Long roomId = roomService.create(userA.getId(),
                new RoomCreateRequest("배달 끊기 방", RoomGoal.DELIVERY, 4, 300, "민지", BosalColor.PINK)).roomId();
        room = roomRepository.findById(roomId).orElseThrow();
        memberA = roomMemberRepository.findByRoom_IdAndUser_IdAndLeftAtIsNull(roomId, userA.getId()).orElseThrow();
        memberB = addMember(room, userB, "지수");
    }

    @Test
    void ROOM_08_1_방장이_내보내면_현재_멤버에서_빠지고_유형은_KICKED다() throws Exception {
        kick(userA, memberB).andExpect(status().isOk());

        RoomMember kicked = roomMemberRepository.findById(memberB.getId()).orElseThrow();
        assertThat(kicked.isCurrent()).isFalse();
        assertThat(kicked.getLeftAt()).isEqualTo(NOW);
        assertThat(kicked.getLeaveType()).isEqualTo(LeaveType.KICKED);
    }

    @Test
    void ROOM_08_2_방장이_아니면_ROOM_007을_돌려주고_대상은_그대로다() throws Exception {
        RoomMember memberC = addMember(room, newUser(), "하린");

        kick(userB, memberC)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ROOM_007"));

        assertThat(roomMemberRepository.findById(memberC.getId()).orElseThrow().isCurrent()).isTrue();
    }

    @Test
    void ROOM_08_3_오늘_회차가_투표_중이면_ROOM_008을_돌려준다() throws Exception {
        saveRound(room, NOW.toLocalDate(), RoundStatus.VOTING);

        kick(userA, memberB)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ROOM_008"));

        assertThat(roomMemberRepository.findById(memberB.getId()).orElseThrow().isCurrent()).isTrue();
    }

    @Test
    void ROOM_08_4_어제_회차가_투표_중이면_ROOM_008을_돌려준다() throws Exception {
        clock.set(NOW.toLocalDate().atTime(10, 0));
        saveRound(room, NOW.toLocalDate().minusDays(1), RoundStatus.VOTING);

        kick(userA, memberB)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ROOM_008"));

        assertThat(roomMemberRepository.findById(memberB.getId()).orElseThrow().isCurrent()).isTrue();
    }

    @Test
    void 인증이_마감됐는데_투표가_아직_안_열린_회차가_있으면_ROOM_008을_돌려준다() throws Exception {
        clock.set(NOW.toLocalDate().atTime(3, 0, 30));
        saveRound(room, NOW.toLocalDate().minusDays(1), RoundStatus.CERTIFYING);

        kick(userA, memberB)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ROOM_008"));
    }

    @Test
    void ROOM_08_5_내보내진_뒤_그_방을_조회하면_ROOM_002를_돌려준다() throws Exception {
        kick(userA, memberB).andExpect(status().isOk());

        mockMvc.perform(get("/api/rooms/{roomId}/today", room.getId()).header("Authorization", bearer(userB)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ROOM_002"));
    }

    @Test
    void ROOM_08_6_마지막_인증과_내보내기가_동시에_오면_하나씩_처리된다() {
        User userC = newUser();
        User userD = newUser();
        RoomMember memberC = addMember(room, userC, "하린");
        addMember(room, userD, "도윤");
        Round round = roundRepository.save(Round.create(room, NOW.toLocalDate()));
        for (RoomMember member : List.of(memberA, memberB, memberC)) {
            certificationRepository.save(Certification.create(
                    round, member, CertificationResult.ENDURED, "집밥 해먹었어요", null, NOW.minusHours(1)));
        }

        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            CountDownLatch ready = new CountDownLatch(2);
            CountDownLatch start = new CountDownLatch(1);
            CompletableFuture<Void> certify = CompletableFuture.runAsync(() -> {
                ready.countDown();
                await(start);
                certificationService.create(userD.getId(), room.getId(),
                        new CertificationCreateRequest(CertificationResult.ENDURED, "집밥 해먹었어요", null, null), PreparedPhoto.NONE,
                        LocalDateTime.now(clock));
            }, executor);
            CompletableFuture<ErrorCode> kick = CompletableFuture.supplyAsync(() -> {
                ready.countDown();
                await(start);
                try {
                    roomService.kick(userA.getId(), room.getId(), memberB.getId());
                    return null;
                } catch (BaseException e) {
                    return e.getErrorCode();
                }
            }, executor);
            await(ready);
            start.countDown();
            certify.join();
            ErrorCode kickError = kick.join();

            assertThat(roundRepository.findById(round.getId()).orElseThrow().getStatus()).isEqualTo(RoundStatus.VOTING);
            List<Long> roster = roundParticipantRepository.findByRound_Id(round.getId()).stream()
                    .map(participant -> participant.getMember().getId())
                    .toList();
            if (kickError == null) {
                assertThat(roster).doesNotContain(memberB.getId()).hasSize(3);
                assertThat(roomMemberRepository.findById(memberB.getId()).orElseThrow().getLeaveType())
                        .isEqualTo(LeaveType.KICKED);
            } else {
                assertThat(kickError).isEqualTo(BaseErrorCode.KICK_DURING_VOTE);
                assertThat(roster).contains(memberB.getId()).hasSize(4);
                assertThat(roomMemberRepository.findById(memberB.getId()).orElseThrow().isCurrent()).isTrue();
            }
        } finally {
            executor.shutdown();
        }
    }

    @Test
    void ROOM_08_7_이미_나간_멤버면_ROOM_009를_돌려주고_나간_유형은_그대로다() throws Exception {
        leave(memberB.getId(), NOW.minusHours(1), LeaveType.LEFT);

        kick(userA, memberB)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ROOM_009"));

        assertThat(roomMemberRepository.findById(memberB.getId()).orElseThrow().getLeaveType()).isEqualTo(LeaveType.LEFT);
    }

    @Test
    void 다른_방_멤버면_ROOM_009를_돌려준다() throws Exception {
        RoomMember otherRoomMember = addMember(createRoom(4), newUser(), "서연");

        kick(userA, otherRoomMember)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ROOM_009"));

        assertThat(roomMemberRepository.findById(otherRoomMember.getId()).orElseThrow().isCurrent()).isTrue();
    }

    @Test
    void ROOM_08_8_방장_본인이면_ROOM_010을_돌려주고_그대로_방장이다() throws Exception {
        kick(userA, memberA)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("ROOM_010"));

        assertThat(roomRepository.findById(room.getId()).orElseThrow().getOwnerMemberId()).isEqualTo(memberA.getId());
        assertThat(roomMemberRepository.findById(memberA.getId()).orElseThrow().isCurrent()).isTrue();
    }

    @Test
    void ROOM_08_9_내보내도_한_줄과_금액은_남고_기록은_떠난_보살로_보인다() throws Exception {
        Round round = saveRound(room, NOW.toLocalDate().minusDays(1), RoundStatus.VOTING);
        Certification certification = certificationRepository.save(Certification.create(
                round, memberB, CertificationResult.FAILED, "치킨 시켰어요", 23000, NOW.minusDays(1)));
        round.announce(NOW.minusHours(1), RoundResultType.PICKED, memberB);
        roundRepository.save(round);

        kick(userA, memberB).andExpect(status().isOk());

        Certification kept = certificationRepository.findById(certification.getId()).orElseThrow();
        assertThat(kept.getMemo()).isEqualTo("치킨 시켰어요");
        assertThat(kept.getSpentAmount()).isEqualTo(23000);
        mockMvc.perform(get("/api/rounds/{roundId}/result", round.getId()).header("Authorization", bearer(userA)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.picked.memberId").value(memberB.getId()))
                .andExpect(jsonPath("$.data.picked.isLeft").value(true))
                .andExpect(jsonPath("$.data.picked.spentAmount").value(23000));
    }

    @Test
    void ROOM_08_10_내보내서_전원_인증이_되면_바로_투표가_시작되고_명단은_남은_멤버다() throws Exception {
        RoomMember memberC = addMember(room, newUser(), "하린");
        RoomMember memberD = addMember(room, newUser(), "도윤");
        Round round = roundRepository.save(Round.create(room, NOW.toLocalDate()));
        for (RoomMember member : List.of(memberA, memberB, memberC)) {
            certificationRepository.save(Certification.create(
                    round, member, CertificationResult.ENDURED, "집밥 해먹었어요", null, NOW.minusHours(1)));
        }

        kick(userA, memberD).andExpect(status().isOk());

        assertThat(roundRepository.findById(round.getId()).orElseThrow().getStatus()).isEqualTo(RoundStatus.VOTING);
        assertThat(roundParticipantRepository.findByRound_Id(round.getId()))
                .extracting(participant -> participant.getMember().getId())
                .containsExactlyInAnyOrder(memberA.getId(), memberB.getId(), memberC.getId());
    }

    @Test
    void 멤버가_아니면_ROOM_002를_돌려준다() throws Exception {
        kick(newUser(), memberB)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ROOM_002"));
    }

    @Test
    void 삭제된_방이면_ROOM_005를_돌려준다() throws Exception {
        deleteRoom(room);

        kick(userA, memberB)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ROOM_005"));
    }

    private ResultActions kick(User owner, RoomMember target) throws Exception {
        return mockMvc.perform(delete("/api/rooms/{roomId}/members/{memberId}", room.getId(), target.getId())
                .header("Authorization", bearer(owner)));
    }
}
