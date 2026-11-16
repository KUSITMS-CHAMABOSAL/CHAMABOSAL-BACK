package com.chamabosal.domain.room.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.chamabosal.domain.certification.entity.Certification;
import com.chamabosal.domain.certification.entity.CertificationResult;
import com.chamabosal.domain.nabal.entity.NabalLoss;
import com.chamabosal.domain.nabal.repository.NabalLossRepository;
import com.chamabosal.domain.pot.entity.RoomPot;
import com.chamabosal.domain.room.dto.request.RoomCreateRequest;
import com.chamabosal.domain.room.entity.LeaveType;
import com.chamabosal.domain.room.entity.Room;
import com.chamabosal.domain.room.entity.RoomGoal;
import com.chamabosal.domain.room.entity.RoomMember;
import com.chamabosal.domain.round.entity.Round;
import com.chamabosal.domain.round.entity.RoundResultType;
import com.chamabosal.domain.round.entity.RoundStatus;
import com.chamabosal.domain.tap.entity.Tap;
import com.chamabosal.domain.tap.repository.TapRepository;
import com.chamabosal.domain.user.entity.User;
import java.time.LocalDate;
import java.time.LocalDateTime;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.ResultActions;

/** 오늘 탭 (ROOM-02). 공통 Given: 11/1에 A가 만든 방에 B, C가 있고 지금은 11/11 12:00(11/11 회차)이다. */
class RoomTodayTest extends RoomTestSupport {

    private static final LocalDate TODAY = NOW.toLocalDate();
    private static final LocalDateTime CREATED_AT = LocalDateTime.of(2026, 11, 1, 12, 0);

    @Autowired
    NabalLossRepository nabalLossRepository;

    @Autowired
    TapRepository tapRepository;

    private User userA;
    private User userB;
    private User userC;
    private Room room;
    private RoomMember memberA;
    private RoomMember memberB;
    private RoomMember memberC;

    @BeforeEach
    void setUp() {
        clock.set(CREATED_AT);
        userA = newUser();
        userB = newUser();
        userC = newUser();
        Long roomId = roomService.create(userA.getId(),
                new RoomCreateRequest("배달 끊기 방", RoomGoal.DELIVERY, 4, 300, "민지")).roomId();
        room = roomRepository.findById(roomId).orElseThrow();
        memberA = roomMemberRepository.findByRoom_IdAndUser_IdAndLeftAtIsNull(roomId, userA.getId()).orElseThrow();
        memberB = join(userB, "지수", CREATED_AT.plusHours(1));
        memberC = join(userC, "하린", CREATED_AT.plusHours(2));
        clock.set(NOW);
    }

    @AfterEach
    void cleanUp() {
        tapRepository.deleteAll();
        nabalLossRepository.deleteAll();
    }

    @Test
    void ROOM_02_1_인증한_인원과_인증_전_멤버의_빈_인증_정보를_돌려준다() throws Exception {
        Round round = todayRound(RoundStatus.CERTIFYING);
        certify(round, memberA, NOW.minusHours(2));
        certify(round, memberB, NOW.minusHours(1));

        today(userC)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.round.certifiedCount").value(2))
                .andExpect(jsonPath("$.data.round.isAllCertified").value(false))
                .andExpect(jsonPath("$.data.members[2].memberId").value(memberC.getId()))
                .andExpect(jsonPath("$.data.members[2].isMe").value(true))
                .andExpect(jsonPath("$.data.members[2].certification").doesNotExist())
                .andExpect(jsonPath("$.data.members[0].certification.result").value("ENDURED"))
                .andExpect(jsonPath("$.data.members[0].certification.lockReason").value("TIME_OVER"));
    }

    @Test
    void ROOM_02_2_멤버가_아니면_ROOM_002를_돌려준다() throws Exception {
        today(newUser())
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ROOM_002"));
    }

    @Test
    void ROOM_02_3_삭제된_방이면_ROOM_005를_돌려준다() throws Exception {
        deleteRoom(room);

        today(userA)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ROOM_005"));
    }

    @Test
    void ROOM_02_4_이전_회차_결과가_발표됐으면_인증이_열려_있다() throws Exception {
        Round yesterday = saveRound(room, TODAY.minusDays(1), RoundStatus.VOTING);
        yesterday.announce(NOW.minusHours(1), RoundResultType.ALL_SAFE, null);
        roundRepository.save(yesterday);

        today(userA)
                .andExpect(jsonPath("$.data.round.isCertificationOpen").value(true))
                .andExpect(jsonPath("$.data.round.status").value("CERTIFYING"));
    }

    @Test
    void ROOM_02_5_전원_인증으로_투표가_시작되면_투표_중이고_잠금_사유는_VOTE_STARTED다() throws Exception {
        Round round = todayRound(RoundStatus.CERTIFYING);
        for (RoomMember member : new RoomMember[]{memberA, memberB, memberC}) {
            certify(round, member, NOW.minusMinutes(3));
        }
        round.startVote(NOW.minusMinutes(2));
        roundRepository.save(round);

        today(userA)
                .andExpect(jsonPath("$.data.round.status").value("VOTING"))
                .andExpect(jsonPath("$.data.round.isAllCertified").value(true))
                .andExpect(jsonPath("$.data.round.isCertificationOpen").value(false))
                .andExpect(jsonPath("$.data.members[0].certification.lockReason").value("VOTE_STARTED"));
    }

    @Test
    void ROOM_02_6_회차_행이_없으면_회차_ID는_비어_있고_인증_0명이다() throws Exception {
        today(userA)
                .andExpect(jsonPath("$.data.round.roundId").doesNotExist())
                .andExpect(jsonPath("$.data.round.status").value("CERTIFYING"))
                .andExpect(jsonPath("$.data.round.certifiedCount").value(0));
    }

    @Test
    void ROOM_02_7_내가_인증하면_전원_인증이_되면_마지막_인증자다() throws Exception {
        Round round = todayRound(RoundStatus.CERTIFYING);
        certify(round, memberA, NOW.minusMinutes(1));
        certify(round, memberB, NOW.minusMinutes(1));

        today(userC).andExpect(jsonPath("$.data.me.isLastCertifier").value(true));
        today(userA).andExpect(jsonPath("$.data.me.isLastCertifier").value(false));
    }

    @Test
    void ROOM_02_16_투표가_열리지_않는_2명_방에서는_마지막_인증자가_아니다() throws Exception {
        leave(memberC.getId(), NOW.minusHours(1), LeaveType.LEFT);
        Round round = todayRound(RoundStatus.CERTIFYING);
        certify(round, memberA, NOW.minusMinutes(1));

        today(userB)
                .andExpect(jsonPath("$.data.round.isCertificationOpen").value(true))
                .andExpect(jsonPath("$.data.me.isLastCertifier").value(false));
    }

    @Test
    void ROOM_02_9_이전_회차_투표_중에는_인증이_열리지_않고_마지막_인증자도_아니다() throws Exception {
        saveRound(room, TODAY.minusDays(1), RoundStatus.VOTING);

        today(userC)
                .andExpect(jsonPath("$.data.round.isCertificationOpen").value(false))
                .andExpect(jsonPath("$.data.me.isLastCertifier").value(false));
    }

    @Test
    void ROOM_02_10_이전_회차가_없으면_인증이_열려_있다() throws Exception {
        today(userA).andExpect(jsonPath("$.data.round.isCertificationOpen").value(true));
    }

    @Test
    void ROOM_02_11_인증하고_나간_멤버는_세지_않는다() throws Exception {
        join(newUser(), "도윤", CREATED_AT.plusHours(3));
        Round round = todayRound(RoundStatus.CERTIFYING);
        certify(round, memberA, NOW.minusHours(1));
        certify(round, memberB, NOW.minusHours(1));
        leave(memberB.getId(), NOW.minusMinutes(30), LeaveType.LEFT);

        today(userA)
                .andExpect(jsonPath("$.data.round.certifiedCount").value(1))
                .andExpect(jsonPath("$.data.round.isAllCertified").value(false))
                .andExpect(jsonPath("$.data.members.length()").value(3));
    }

    @Test
    void ROOM_02_12_이번_달_참은_횟수는_이_방_기록만_센다() throws Exception {
        for (int day = 2; day <= 3; day++) {
            certify(saveRound(room, LocalDate.of(2026, 11, day), RoundStatus.ANNOUNCED), memberB,
                    LocalDate.of(2026, 11, day).atTime(20, 0));
        }
        Room otherRoom = createRoom(4);
        RoomMember otherB = roomMemberRepository.save(RoomMember.create(otherRoom, userB, "지수", unusedColor(otherRoom), CREATED_AT));
        for (int day = 2; day <= 4; day++) {
            certify(saveRound(otherRoom, LocalDate.of(2026, 11, day), RoundStatus.ANNOUNCED), otherB,
                    LocalDate.of(2026, 11, day).atTime(20, 0));
        }
        certify(saveRound(room, LocalDate.of(2026, 10, 31), RoundStatus.ANNOUNCED), memberB, CREATED_AT.minusDays(1));

        today(userA).andExpect(jsonPath("$.data.members[1].monthlyEnduredCount").value(2));
    }

    @Test
    void ROOM_02_13_이번_달_지목_횟수는_방어_성공을_세지_않는다() throws Exception {
        Round picked = saveRound(room, LocalDate.of(2026, 11, 5), RoundStatus.VOTING);
        picked.announce(NOW.minusDays(5), RoundResultType.PICKED, memberB);
        roundRepository.save(picked);
        Round defended = saveRound(room, LocalDate.of(2026, 11, 6), RoundStatus.VOTING);
        defended.announce(NOW.minusDays(4), RoundResultType.DEFENDED, memberB);
        ReflectionTestUtils.setField(defended, "resultType", RoundResultType.DEFENDED);
        roundRepository.save(defended);

        today(userA).andExpect(jsonPath("$.data.members[1].monthlyPickedCount").value(1));
    }

    @Test
    void ROOM_02_14_어제_회차가_투표_중이면_투표_중인_회차가_있고_오늘_회차는_인증_중이다() throws Exception {
        saveRound(room, TODAY.minusDays(1), RoundStatus.VOTING);

        today(userA)
                .andExpect(jsonPath("$.data.room.hasVotingRound").value(true))
                .andExpect(jsonPath("$.data.round.status").value("CERTIFYING"))
                .andExpect(jsonPath("$.data.round.roundId").doesNotExist());
    }

    @Test
    void ROOM_02_15_같이_참은_일수는_만든_날_0일부터_센다() throws Exception {
        clock.set(CREATED_AT.plusHours(5));
        today(userA).andExpect(jsonPath("$.data.room.daysTogether").value(0));

        clock.set(LocalDateTime.of(2026, 11, 8, 12, 0));
        today(userA).andExpect(jsonPath("$.data.room.daysTogether").value(7));
    }

    @Test
    void 투표_없음_회차에서_인증을_삭제한_멤버만_다시_인증이_열려_있다() throws Exception {
        Round round = todayRound(RoundStatus.NO_VOTE);
        Certification deleted = certify(round, memberA, NOW.minusMinutes(5));
        deleted.delete(NOW.minusMinutes(2));
        certificationRepository.save(deleted);
        certify(round, memberB, NOW.minusMinutes(5));

        today(userA).andExpect(jsonPath("$.data.round.isCertificationOpen").value(true));
        today(userB).andExpect(jsonPath("$.data.round.isCertificationOpen").value(false));
    }

    @Test
    void 방_정보와_임시_값을_돌려준다() throws Exception {
        today(userA)
                .andExpect(jsonPath("$.data.room.name").value("배달 끊기 방"))
                .andExpect(jsonPath("$.data.room.memberCount").value(3))
                .andExpect(jsonPath("$.data.room.isOwner").value(true))
                .andExpect(jsonPath("$.data.pot.amount").value(0))
                .andExpect(jsonPath("$.data.pot.goalAmount").value(300))
                .andExpect(jsonPath("$.data.me.receivedPokeCount").value(0))
                .andExpect(jsonPath("$.data.members[0].equippedItemCodes.length()").value(0));
        today(userB).andExpect(jsonPath("$.data.room.isOwner").value(false));
    }

    @Test
    void 멤버마다_빠진_나발_번호를_돌려준다() throws Exception {
        nabalLossRepository.save(NabalLoss.create(userB, room, TODAY.minusDays(2), 7));
        nabalLossRepository.save(NabalLoss.create(userB, room, TODAY.minusDays(1), 3));

        today(userA)
                .andExpect(jsonPath("$.data.members[1].fallenNabalNumbers[0]").value(3))
                .andExpect(jsonPath("$.data.members[1].fallenNabalNumbers[1]").value(7))
                .andExpect(jsonPath("$.data.members[0].fallenNabalNumbers.length()").value(0));
    }

    @Test
    void 진입_항목으로_재생할_톡톡과_안_본_결과와_축하를_돌려준다() throws Exception {
        Round round = saveRound(room, TODAY.minusDays(1), RoundStatus.VOTING);
        round.announce(NOW.minusHours(Round.TAP_HOURS), RoundResultType.PICKED, memberA);
        roundRepository.save(round);
        tapRepository.save(Tap.create(round, memberB, 3));
        ReflectionTestUtils.setField(room, "potCompletedCount", 1);
        roomRepository.save(room);

        today(userA)
                .andExpect(jsonPath("$.data.entry.unplayedTapRoundId").value(round.getId()))
                .andExpect(jsonPath("$.data.entry.unseenResultRoundId").value(round.getId()))
                .andExpect(jsonPath("$.data.entry.hasUnseenPotCelebration").value(true));
        today(userB).andExpect(jsonPath("$.data.entry.unplayedTapRoundId").doesNotExist());
    }

    @Test
    void 투표_마감_5시간이_지나_전달되기_전의_톡톡은_재생할_항목이_아니다() throws Exception {
        Round round = saveRound(room, TODAY.minusDays(1), RoundStatus.VOTING);
        round.announce(NOW.minusHours(Round.TAP_HOURS).plusMinutes(1), RoundResultType.PICKED, memberA);
        roundRepository.save(round);
        tapRepository.save(Tap.create(round, memberB, 3));

        today(userA)
                .andExpect(jsonPath("$.data.entry.unplayedTapRoundId").doesNotExist())
                .andExpect(jsonPath("$.data.entry.unseenResultRoundId").value(round.getId()));
    }

    @Test
    void 사진이_있는_인증은_사진_URL을_돌려주고_없으면_비운다() throws Exception {
        Round round = todayRound(RoundStatus.CERTIFYING);
        Certification withPhoto = certify(round, memberA, NOW.minusMinutes(5));
        withPhoto.replaceImage("certifications/photo-a");
        certificationRepository.save(withPhoto);
        certify(round, memberB, NOW.minusMinutes(5));

        today(userA)
                .andExpect(jsonPath("$.data.members[0].certification.imageUrl")
                        .value("memory://view/certifications/photo-a"))
                .andExpect(jsonPath("$.data.members[1].certification.imageUrl").doesNotExist());
    }

    @Test
    void 지금_쌓고_있는_솥의_진행도와_목표를_돌려준다() throws Exception {
        RoomPot pot = RoomPot.first(room);
        pot.add(186);
        roomPotRepository.save(pot);

        today(userA)
                .andExpect(jsonPath("$.data.pot.amount").value(186))
                .andExpect(jsonPath("$.data.pot.goalAmount").value(300));
    }

    private RoomMember join(User user, String nickname, LocalDateTime joinedAt) {
        return roomMemberRepository.save(RoomMember.create(room, user, nickname, unusedColor(room), joinedAt));
    }

    private Round todayRound(RoundStatus status) {
        return saveRound(room, TODAY, status);
    }

    private Certification certify(Round round, RoomMember member, LocalDateTime certifiedAt) {
        return certificationRepository.save(Certification.create(
                round, member, CertificationResult.ENDURED, "집밥 해먹었어요", null, certifiedAt));
    }

    private ResultActions today(User user) throws Exception {
        return mockMvc.perform(get("/api/rooms/{roomId}/today", room.getId()).header("Authorization", bearer(user)));
    }
}
