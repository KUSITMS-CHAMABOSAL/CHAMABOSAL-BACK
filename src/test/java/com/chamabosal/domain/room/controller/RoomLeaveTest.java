package com.chamabosal.domain.room.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.chamabosal.domain.certification.entity.Certification;
import com.chamabosal.domain.certification.entity.CertificationResult;
import com.chamabosal.domain.room.dto.request.RoomCreateRequest;
import com.chamabosal.domain.room.entity.LeaveType;
import com.chamabosal.domain.room.entity.Room;
import com.chamabosal.domain.room.entity.RoomGoal;
import com.chamabosal.domain.room.entity.RoomMember;
import com.chamabosal.domain.round.entity.Round;
import com.chamabosal.domain.round.entity.RoundStatus;
import com.chamabosal.domain.user.entity.User;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.ResultActions;

class RoomLeaveTest extends RoomTestSupport {

    private User userA;
    private Room room;
    private RoomMember memberA;

    /** A가 방장인 방. */
    @BeforeEach
    void setUp() {
        userA = newUser();
        Long roomId = roomService.create(userA.getId(),
                new RoomCreateRequest("배달 끊기 방", RoomGoal.DELIVERY, 4, "민지")).roomId();
        room = roomRepository.findById(roomId).orElseThrow();
        memberA = roomMemberRepository.findByRoom_IdAndUser_IdAndLeftAtIsNull(roomId, userA.getId()).orElseThrow();
    }

    @Test
    void ROOM_07_1_나가면_현재_멤버에서_빠지고_유형은_LEFT다() throws Exception {
        addMember(room, newUser(), "지수");

        leave(userA)
                .andExpect(status().isOk());

        RoomMember left = roomMemberRepository.findById(memberA.getId()).orElseThrow();
        assertThat(left.isCurrent()).isFalse();
        assertThat(left.getLeftAt()).isEqualTo(NOW);
        assertThat(left.getLeaveType()).isEqualTo(LeaveType.LEFT);
    }

    @Test
    void ROOM_07_4_방장이_나가면_가장_먼저_들어온_멤버가_방장이_된다() throws Exception {
        RoomMember memberC = roomMemberRepository.save(
                RoomMember.create(room, newUser(), "하린", unusedColor(room), NOW.minusDays(1)));
        RoomMember memberB = roomMemberRepository.save(
                RoomMember.create(room, newUser(), "지수", unusedColor(room), NOW.minusDays(2)));

        leave(userA)
                .andExpect(status().isOk());

        assertThat(roomRepository.findById(room.getId()).orElseThrow().getOwnerMemberId())
                .isEqualTo(memberB.getId())
                .isNotEqualTo(memberC.getId());
    }

    @Test
    void ROOM_07_5_마지막_멤버가_나가면_방이_삭제되고_참여_방_수에서_빠진다() throws Exception {
        leave(userA)
                .andExpect(status().isOk());

        assertThat(roomRepository.findById(room.getId()).orElseThrow().isDeleted()).isTrue();
        assertThat(roomMemberRepository.countByUser_IdAndLeftAtIsNullAndRoom_DeletedAtIsNull(userA.getId())).isZero();
    }

    @Test
    void ROOM_07_8_둘이_동시에_나가면_방은_한_번만_삭제되고_남은_멤버가_없다() {
        User userB = newUser();
        addMember(room, userB, "지수");

        leaveConcurrently(List.of(userA, userB));

        Room found = roomRepository.findById(room.getId()).orElseThrow();
        assertThat(found.isDeleted()).isTrue();
        assertThat(found.getDeletedAt()).isEqualTo(NOW);
        assertThat(roomMemberRepository.findByRoom_IdAndLeftAtIsNull(room.getId())).isEmpty();
    }

    @Test
    void ROOM_07_11_나가서_전원_인증이_되면_바로_투표가_시작되고_명단은_남은_멤버다() throws Exception {
        RoomMember memberB = addMember(room, newUser(), "지수");
        RoomMember memberC = addMember(room, newUser(), "하린");
        User userD = newUser();
        addMember(room, userD, "도윤");
        Round round = saveRound(room, NOW.toLocalDate(), RoundStatus.CERTIFYING);
        for (RoomMember member : List.of(memberA, memberB, memberC)) {
            certificationRepository.save(Certification.create(
                    round, member, CertificationResult.ENDURED, "집밥 해먹었어요", null, NOW.minusHours(1)));
        }

        leave(userD).andExpect(status().isOk());

        assertThat(roundRepository.findById(round.getId()).orElseThrow().getStatus()).isEqualTo(RoundStatus.VOTING);
        assertThat(roundParticipantRepository.findByRound_Id(round.getId()))
                .extracting(participant -> participant.getMember().getId())
                .containsExactlyInAnyOrder(memberA.getId(), memberB.getId(), memberC.getId());
    }

    private ResultActions leave(User user) throws Exception {
        return mockMvc.perform(delete("/api/rooms/{roomId}/members/me", room.getId())
                .header("Authorization", bearer(user)));
    }

    private void leaveConcurrently(List<User> users) {
        ExecutorService executor = Executors.newFixedThreadPool(users.size());
        try {
            CountDownLatch ready = new CountDownLatch(users.size());
            CountDownLatch start = new CountDownLatch(1);
            List<CompletableFuture<Void>> futures = users.stream()
                    .map(user -> CompletableFuture.runAsync(() -> {
                        ready.countDown();
                        await(start);
                        roomService.leave(user.getId(), room.getId());
                    }, executor))
                    .toList();
            await(ready);
            start.countDown();
            futures.forEach(CompletableFuture::join);
        } finally {
            executor.shutdown();
        }
    }
}
