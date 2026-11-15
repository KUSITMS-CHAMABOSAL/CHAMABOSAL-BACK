package com.chamabosal.domain.room.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.chamabosal.domain.certification.entity.Certification;
import com.chamabosal.domain.certification.entity.CertificationResult;
import com.chamabosal.domain.rice.entity.RiceHistory;
import com.chamabosal.domain.rice.entity.RiceReason;
import com.chamabosal.domain.rice.entity.RiceRefType;
import com.chamabosal.domain.rice.service.RiceService;
import com.chamabosal.domain.room.dto.request.RoomCreateRequest;
import com.chamabosal.domain.room.entity.LeaveType;
import com.chamabosal.domain.room.entity.Room;
import com.chamabosal.domain.room.entity.RoomGoal;
import com.chamabosal.domain.room.entity.RoomMember;
import com.chamabosal.domain.round.entity.Round;
import com.chamabosal.domain.round.entity.RoundResultType;
import com.chamabosal.domain.round.entity.RoundStatus;
import com.chamabosal.domain.user.entity.User;
import com.chamabosal.domain.vote.service.VoteCloseScheduler;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.ResultActions;

class RoomLeaveTest extends RoomTestSupport {

    @Autowired
    RiceService riceService;

    @Autowired
    VoteCloseScheduler voteCloseScheduler;

    private User userA;
    private Room room;
    private RoomMember memberA;

    /** A가 방장인 방. */
    @BeforeEach
    void setUp() {
        userA = newUser();
        Long roomId = roomService.create(userA.getId(),
                new RoomCreateRequest("배달 끊기 방", RoomGoal.DELIVERY, 4, 300, "민지")).roomId();
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
    void ROOM_07_2_나가도_한_줄과_금액은_남고_기록은_떠난_보살로_보인다() throws Exception {
        User userB = newUser();
        addMember(room, userB, "지수");
        Round round = saveRound(room, NOW.toLocalDate().minusDays(1), RoundStatus.VOTING);
        Certification certification = certificationRepository.save(Certification.create(
                round, memberA, CertificationResult.FAILED, "치킨 시켰어요", 23000, NOW.minusDays(1)));
        round.announce(NOW.minusHours(1), RoundResultType.PICKED, memberA);
        roundRepository.save(round);

        leave(userA).andExpect(status().isOk());

        Certification kept = certificationRepository.findById(certification.getId()).orElseThrow();
        assertThat(kept.getMemo()).isEqualTo("치킨 시켰어요");
        assertThat(kept.getSpentAmount()).isEqualTo(23000);
        mockMvc.perform(get("/api/rounds/{roundId}/result", round.getId()).header("Authorization", bearer(userB)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.picked.memberId").value(memberA.getId()))
                .andExpect(jsonPath("$.data.picked.isLeft").value(true))
                .andExpect(jsonPath("$.data.picked.spentAmount").value(23000));
    }

    @Test
    void ROOM_07_3_다른_방에서_모은_공양미는_나가도_그대로다() throws Exception {
        addMember(room, newUser(), "지수");
        riceHistoryRepository.save(RiceHistory.create(
                userA, createRoom(4), 120, RiceReason.CERTIFICATION, RiceRefType.CERTIFICATION, 1L));

        leave(userA).andExpect(status().isOk());

        assertThat(riceService.getDisplayBalance(userA.getId())).isEqualTo(120);
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
    void ROOM_07_9_투표_중_마지막_멤버가_나가면_회차는_결과_없이_남고_투표_적립은_나가기_차감만_된다() throws Exception {
        RoomMember memberB = addMember(room, newUser(), "지수");
        RoomMember memberC = addMember(room, newUser(), "하린");
        Round round = roundRepository.save(Round.create(room, NOW.toLocalDate().minusDays(1)));
        round.startVote(NOW.minusHours(9));
        roundRepository.save(round);
        riceHistoryRepository.save(RiceHistory.create(
                userA, room, RiceService.VOTE_REWARD, RiceReason.VOTE, RiceRefType.VOTE, 1L));
        leave(memberB.getId(), NOW.minusHours(2), LeaveType.LEFT);
        leave(memberC.getId(), NOW.minusHours(1), LeaveType.LEFT);

        leave(userA).andExpect(status().isOk());
        clock.set(round.getVoteClosesAt().plusMinutes(5));
        voteCloseScheduler.closeDueVotes();

        assertThat(roomRepository.findById(room.getId()).orElseThrow().isDeleted()).isTrue();
        Round kept = roundRepository.findById(round.getId()).orElseThrow();
        assertThat(kept.getStatus()).isEqualTo(RoundStatus.VOTING);
        assertThat(kept.getResultType()).isNull();
        // 회차가 결과 없이 남아도 투표 적립은 그대로고, 빠지는 것은 나가기 차감(RICE-03) 한 번뿐이다.
        assertThat(riceHistoryRepository.findAll())
                .filteredOn(history -> history.getUser().getId().equals(userA.getId()))
                .extracting(RiceHistory::getReason, RiceHistory::getDelta)
                .containsExactlyInAnyOrder(
                        tuple(RiceReason.VOTE, RiceService.VOTE_REWARD),
                        tuple(RiceReason.LEAVE, -RiceService.VOTE_REWARD));
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
