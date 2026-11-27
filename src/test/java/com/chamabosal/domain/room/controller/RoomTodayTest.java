package com.chamabosal.domain.room.controller;

import static org.hamcrest.Matchers.contains;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.chamabosal.domain.certification.entity.Certification;
import com.chamabosal.domain.certification.entity.CertificationResult;
import com.chamabosal.domain.nabal.entity.NabalLoss;
import com.chamabosal.domain.nabal.repository.NabalLossRepository;
import com.chamabosal.domain.poke.entity.Poke;
import com.chamabosal.domain.poke.repository.PokeRepository;
import com.chamabosal.domain.pot.entity.RoomPot;
import com.chamabosal.domain.reaction.entity.Reaction;
import com.chamabosal.domain.reaction.entity.ReactionType;
import com.chamabosal.domain.reaction.repository.ReactionRepository;
import com.chamabosal.domain.room.dto.request.RoomCreateRequest;
import com.chamabosal.domain.room.entity.BosalColor;
import com.chamabosal.domain.room.entity.LeaveType;
import com.chamabosal.domain.room.entity.Room;
import com.chamabosal.domain.room.entity.RoomGoal;
import com.chamabosal.domain.room.entity.RoomMember;
import com.chamabosal.domain.round.entity.Round;
import com.chamabosal.domain.round.entity.RoundResultType;
import com.chamabosal.domain.round.entity.RoundStatus;
import com.chamabosal.domain.store.entity.Item;
import com.chamabosal.domain.store.entity.ItemSlot;
import com.chamabosal.domain.store.entity.UserItem;
import com.chamabosal.domain.store.repository.ItemRepository;
import com.chamabosal.domain.store.repository.UserItemRepository;
import com.chamabosal.domain.tap.entity.Tap;
import com.chamabosal.domain.tap.repository.TapRepository;
import com.chamabosal.domain.user.entity.User;
import java.time.LocalDate;
import java.time.LocalDateTime;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
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

    @Autowired
    ReactionRepository reactionRepository;

    @Autowired
    PokeRepository pokeRepository;

    @Autowired
    ItemRepository itemRepository;

    @Autowired
    UserItemRepository userItemRepository;

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
                new RoomCreateRequest("배달 끊기 방", RoomGoal.DELIVERY, 4, 300, "민지", BosalColor.PINK)).roomId();
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
        reactionRepository.deleteAll();
        pokeRepository.deleteAll();
        userItemRepository.deleteAll();
        // 시작 때 넣은 카탈로그는 남기고 테스트가 만든 아이템만 지운다.
        itemRepository.findByCode("EQUIP_HAT").ifPresent(itemRepository::delete);
    }

    @Test
    void ROOM_02_1_인증한_인원과_인증_전_멤버의_빈_인증_정보를_돌려준다() throws Exception {
        Round round = todayRound(RoundStatus.CERTIFYING);
        certify(round, memberA, NOW.minusHours(2));
        certify(round, memberB, NOW.minusHours(1));

        today(userC)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.room.memberLimit").doesNotExist())
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

    // ---------- RESULT-04 이번 달 지목 횟수 (집계 규칙, 표시는 오늘 탭 멤버 목록) ----------

    @Test
    void RESULT_04_2_방어는_세지_않아_PICKED_DEFENDED_PICKED면_2다() throws Exception {
        announce(LocalDate.of(2026, 11, 3), RoundResultType.PICKED, memberB);
        announce(LocalDate.of(2026, 11, 4), RoundResultType.DEFENDED, memberB);
        announce(LocalDate.of(2026, 11, 5), RoundResultType.PICKED, memberB);

        today(userA).andExpect(jsonPath("$.data.members[1].monthlyPickedCount").value(2));
    }

    @Test
    void RESULT_04_3_다른_사람_지목이나_모두_보살이_나와도_초기화하지_않는다() throws Exception {
        announce(LocalDate.of(2026, 11, 3), RoundResultType.PICKED, memberB);
        announce(LocalDate.of(2026, 11, 4), RoundResultType.PICKED, memberC);
        announce(LocalDate.of(2026, 11, 5), RoundResultType.ALL_BOSAL, null);

        today(userA).andExpect(jsonPath("$.data.members[1].monthlyPickedCount").value(1));
    }

    @Test
    void RESULT_04_4_지난달_회차의_지목은_이번_달에_세지_않는다() throws Exception {
        announce(LocalDate.of(2026, 10, 30), RoundResultType.PICKED, memberB);
        announce(LocalDate.of(2026, 10, 31), RoundResultType.PICKED, memberB);

        today(userA).andExpect(jsonPath("$.data.members[1].monthlyPickedCount").value(0));
    }

    @Test
    void RESULT_04_5_나갔다가_6개월_안에_다시_들어오면_이번_달_지목_횟수가_이어진다() throws Exception {
        announce(LocalDate.of(2026, 11, 3), RoundResultType.PICKED, memberB);
        announce(LocalDate.of(2026, 11, 4), RoundResultType.PICKED, memberB);
        // 나갔다가 다시 들어오면 같은 멤버 행을 다시 쓰고 입장 시각만 바뀐다 (RoomMember.rejoin).
        RoomMember rejoined = roomMemberRepository.findById(memberB.getId()).orElseThrow();
        ReflectionTestUtils.setField(rejoined, "joinedAt", NOW.minusDays(1));
        roomMemberRepository.save(rejoined);

        today(userA).andExpect(jsonPath("$.data.members[2].memberId").value(memberB.getId()))
                .andExpect(jsonPath("$.data.members[2].monthlyPickedCount").value(2));
    }

    private void announce(LocalDate roundDate, RoundResultType resultType, RoomMember picked) {
        Round round = saveRound(room, roundDate, RoundStatus.VOTING);
        round.announce(roundDate.plusDays(1).atTime(15, 0), resultType, picked);
        roundRepository.save(round);
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
    void 배치가_투표를_열기_전인_어제_회차도_투표_중인_회차로_본다() throws Exception {
        saveRound(room, TODAY.minusDays(1), RoundStatus.CERTIFYING);

        today(userA).andExpect(jsonPath("$.data.room.hasVotingRound").value(true));
    }

    @Test
    void ROOM_02_15_같이_참은_일수는_두_번째_멤버가_들어온_날이_1일째다() throws Exception {
        User owner = newUser();
        Room together = createRoomOf(owner);
        joinByInvite(together, newUser(), LocalDateTime.of(2026, 11, 3, 12, 0));

        clock.set(LocalDateTime.of(2026, 11, 3, 18, 0));
        todayOf(together, owner).andExpect(jsonPath("$.data.room.daysTogether").value(1));

        clock.set(LocalDateTime.of(2026, 11, 8, 12, 0));
        todayOf(together, owner).andExpect(jsonPath("$.data.room.daysTogether").value(6));
    }

    @Test
    void ROOM_02_17_혼자인_방은_0일째다() throws Exception {
        User owner = newUser();
        Room alone = createRoomOf(owner);

        clock.set(LocalDateTime.of(2026, 11, 8, 12, 0));
        todayOf(alone, owner).andExpect(jsonPath("$.data.room.daysTogether").value(0));
    }

    @Test
    void ROOM_02_18_혼자_남으면_0일째다() throws Exception {
        User owner = newUser();
        User second = newUser();
        Room together = createRoomOf(owner);
        joinByInvite(together, second, LocalDateTime.of(2026, 11, 3, 12, 0));
        clock.set(LocalDateTime.of(2026, 11, 5, 12, 0));
        mockMvc.perform(delete("/api/rooms/{roomId}/members/me", together.getId()).header("Authorization", bearer(second)))
                .andExpect(status().isOk());

        clock.set(LocalDateTime.of(2026, 11, 8, 12, 0));
        todayOf(together, owner).andExpect(jsonPath("$.data.room.daysTogether").value(0));
    }

    @Test
    void ROOM_02_20_혼자_남았다가_다시_2명이_되면_그날이_1일째다() throws Exception {
        User owner = newUser();
        User second = newUser();
        Room together = createRoomOf(owner);
        joinByInvite(together, second, LocalDateTime.of(2026, 11, 3, 12, 0));
        clock.set(LocalDateTime.of(2026, 11, 5, 12, 0));
        mockMvc.perform(delete("/api/rooms/{roomId}/members/me", together.getId()).header("Authorization", bearer(second)))
                .andExpect(status().isOk());
        joinByInvite(together, newUser(), LocalDateTime.of(2026, 11, 8, 12, 0));

        clock.set(LocalDateTime.of(2026, 11, 10, 12, 0));
        todayOf(together, owner).andExpect(jsonPath("$.data.room.daysTogether").value(3));
    }

    @Test
    void ROOM_02_19_새벽_3시_전에_들어오면_전날_회차가_1일째다() throws Exception {
        User owner = newUser();
        Room together = createRoomOf(owner);
        joinByInvite(together, newUser(), LocalDateTime.of(2026, 11, 4, 2, 0));

        clock.set(LocalDateTime.of(2026, 11, 4, 12, 0));
        todayOf(together, owner).andExpect(jsonPath("$.data.room.daysTogether").value(2));
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
    void ROOM_02_21_앱을_열_때_톡톡은_띄우지_않고_안_본_결과와_축하를_돌려준다() throws Exception {
        Round round = saveRound(room, TODAY.minusDays(1), RoundStatus.VOTING);
        round.announce(NOW.minusHours(Round.TAP_HOURS), RoundResultType.PICKED, memberA);
        roundRepository.save(round);
        tapRepository.save(Tap.create(round, memberB, 3));
        ReflectionTestUtils.setField(room, "potCompletedCount", 1);
        roomRepository.save(room);

        // 이마 톡톡·목탁은 투표 탭에서만 진행한다 (13번, Q-107).
        // 전달된 톡톡이 있어도 진입 항목은 안 본 결과와 축하 두 가지뿐이다.
        today(userA)
                .andExpect(jsonPath("$.data.entry.length()").value(2))
                .andExpect(jsonPath("$.data.entry.unseenResultRoundId").value(round.getId()))
                .andExpect(jsonPath("$.data.entry.hasUnseenPotCelebration").value(true));
    }


    @Test
    void ROOM_02_23_투표_중에는_안_본_지난_결과를_띄우지_않는다() throws Exception {
        Round previous = saveRound(room, TODAY.minusDays(2), RoundStatus.VOTING);
        previous.announce(NOW.minusDays(1), RoundResultType.PICKED, memberA);
        roundRepository.save(previous);
        saveRound(room, TODAY.minusDays(1), RoundStatus.VOTING);

        // 투표 탭이 투표 중을 먼저 보여줘서 지난 결과를 띄울 곳이 없다 (13번, VOTE-05 5번).
        today(userA).andExpect(jsonPath("$.data.entry.unseenResultRoundId").doesNotExist());
    }
    @Test
    void ROOM_02_22_한_솥_보상은_첫_적립_전후_모두_목표의_10퍼센트를_10석_단위로_반올림한다() throws Exception {
        ReflectionTestUtils.setField(room, "potGoalAmount", 350);
        roomRepository.save(room);

        // 첫 적립 전에는 솥 행이 없어 방 목표 석으로 계산한다 (22번).
        today(userA).andExpect(jsonPath("$.data.pot.rewardAmount").value(40));

        RoomPot pot = RoomPot.first(room);
        pot.add(186);
        roomPotRepository.save(pot);

        // 한 솥 축하(POT-01)와 같은 값이다. 350석의 10%는 35석이지만 10석 단위로 반올림해 40석이다 (Q-81).
        today(userA)
                .andExpect(jsonPath("$.data.pot.goalAmount").value(350))
                .andExpect(jsonPath("$.data.pot.rewardAmount").value(40));
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

    @Test
    void 인증마다_내가_남긴_리액션만_돌려준다() throws Exception {
        Round round = todayRound(RoundStatus.CERTIFYING);
        Certification certificationB = certify(round, memberB, NOW.minusMinutes(5));
        reactionRepository.save(Reaction.create(certificationB, memberA, ReactionType.GOOD));

        today(userA).andExpect(jsonPath("$.data.members[1].certification.myReaction").value("GOOD"));
        today(userC).andExpect(jsonPath("$.data.members[1].certification.myReaction").doesNotExist());
    }

    @Test
    void REACT_01_6_다른_멤버가_남긴_리액션은_수만_보이고_누른_사람은_보이지_않는다() throws Exception {
        Round round = todayRound(RoundStatus.CERTIFYING);
        Certification certificationB = certify(round, memberB, NOW.minusMinutes(5));
        reactionRepository.save(Reaction.create(certificationB, memberA, ReactionType.BAD));

        today(userC)
                .andExpect(jsonPath("$.data.members[1].certification.badCount").value(1))
                .andExpect(jsonPath("$.data.members[1].certification.goodCount").value(0))
                .andExpect(jsonPath("$.data.members[1].certification.myReaction").doesNotExist())
                .andExpect(jsonPath("$.data.members[1].certification.reactions").doesNotExist());
    }

    @Test
    void 인증마다_받은_리액션_수를_돌려준다() throws Exception {
        Round round = todayRound(RoundStatus.CERTIFYING);
        Certification certificationA = certify(round, memberA, NOW.minusMinutes(10));
        Certification certificationB = certify(round, memberB, NOW.minusMinutes(5));
        reactionRepository.save(Reaction.create(certificationB, memberA, ReactionType.GOOD));
        reactionRepository.save(Reaction.create(certificationB, memberC, ReactionType.GOOD));
        reactionRepository.save(Reaction.create(certificationA, memberB, ReactionType.BAD));

        today(userC)
                .andExpect(jsonPath("$.data.members[1].certification.goodCount").value(2))
                .andExpect(jsonPath("$.data.members[1].certification.badCount").value(0))
                .andExpect(jsonPath("$.data.members[0].certification.goodCount").value(0))
                .andExpect(jsonPath("$.data.members[0].certification.badCount").value(1));
    }

    @Test
    void 이번_회차에_내가_받은_콕_수를_돌려준다() throws Exception {
        Round round = todayRound(RoundStatus.CERTIFYING);
        pokeRepository.save(Poke.create(round, memberB, memberA, 3));
        pokeRepository.save(Poke.create(round, memberC, memberA, 2));
        pokeRepository.save(Poke.create(round, memberA, memberB, 4));
        Round yesterday = saveRound(room, TODAY.minusDays(1), RoundStatus.ANNOUNCED);
        pokeRepository.save(Poke.create(yesterday, memberB, memberA, 7));

        today(userA).andExpect(jsonPath("$.data.me.receivedPokeCount").value(5));
        today(userB).andExpect(jsonPath("$.data.me.receivedPokeCount").value(4));
        today(userC).andExpect(jsonPath("$.data.me.receivedPokeCount").value(0));
    }

    @Test
    void STORE_03_5_착용하면_두_방의_오늘_탭에서_같은_착용_목록이_보인다() throws Exception {
        Room otherRoom = createRoom(4);
        RoomMember otherA = addMember(otherRoom, userA, "다른방");
        Item hat = itemRepository.save(Item.create("EQUIP_HAT", "모자", 100, ItemSlot.HAT, 99));
        UserItem owned = UserItem.acquire(userA, hat);
        owned.unequip();
        userItemRepository.save(owned);

        mockMvc.perform(put("/api/users/me/equipped-items").header("Authorization", bearer(userA))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"itemIds\": [" + hat.getId() + "]}"))
                .andExpect(status().isOk());

        today(userA).andExpect(jsonPath("$.data.members[?(@.memberId == " + memberA.getId() + ")].equippedItemCodes[*]")
                .value(contains("EQUIP_HAT")));
        today(userB).andExpect(jsonPath("$.data.members[?(@.memberId == " + memberA.getId() + ")].equippedItemCodes[*]")
                .value(contains("EQUIP_HAT")));
        mockMvc.perform(get("/api/rooms/{roomId}/today", otherRoom.getId()).header("Authorization", bearer(userA)))
                .andExpect(jsonPath("$.data.members[?(@.memberId == " + otherA.getId() + ")].equippedItemCodes[*]")
                        .value(contains("EQUIP_HAT")));
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

    /** 11/1에 owner 혼자 있는 방을 만든다. 초대 토큰은 방마다 달라야 해서 방 ID로 만든다. */
    private Room createRoomOf(User owner) {
        clock.set(CREATED_AT);
        Long roomId = roomService.create(owner.getId(),
                new RoomCreateRequest("같이 참는 방", RoomGoal.DELIVERY, 4, 300, "방장", BosalColor.PINK)).roomId();
        RoomMember ownerMember = roomMemberRepository.findByRoom_IdAndUser_IdAndLeftAtIsNull(roomId, owner.getId())
                .orElseThrow();
        ownerMember.issueInviteToken("token-" + roomId);
        roomMemberRepository.save(ownerMember);
        return roomRepository.findById(roomId).orElseThrow();
    }

    /** 실제 입장 API로 들어와야 두 번째 멤버 입장일이 기록된다. */
    private void joinByInvite(Room target, User user, LocalDateTime at) throws Exception {
        clock.set(at);
        mockMvc.perform(post("/api/invites/{token}/join", "token-" + target.getId())
                        .header("Authorization", bearer(user))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nickname\":\"도윤\"}"))
                .andExpect(status().isCreated());
    }

    private ResultActions todayOf(Room target, User user) throws Exception {
        return mockMvc.perform(get("/api/rooms/{roomId}/today", target.getId()).header("Authorization", bearer(user)));
    }

    private ResultActions today(User user) throws Exception {
        return mockMvc.perform(get("/api/rooms/{roomId}/today", room.getId()).header("Authorization", bearer(user)));
    }
}
