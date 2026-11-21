package com.chamabosal.domain.vote.service;

import com.chamabosal.domain.room.dto.request.RoomSeenResultUpdateRequest;
import com.chamabosal.domain.tap.repository.TapRepository;
import com.chamabosal.domain.room.service.RoomService;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.chamabosal.domain.certification.entity.Certification;
import com.chamabosal.domain.certification.entity.CertificationResult;
import com.chamabosal.domain.certification.repository.CertificationRepository;
import com.chamabosal.domain.pot.repository.PotContributionRepository;
import com.chamabosal.domain.rice.repository.RiceHistoryRepository;
import com.chamabosal.domain.pot.repository.RoomPotRepository;
import com.chamabosal.domain.room.entity.BosalColor;
import com.chamabosal.domain.room.entity.LeaveType;
import com.chamabosal.domain.room.entity.Room;
import com.chamabosal.domain.room.entity.RoomGoal;
import com.chamabosal.domain.room.entity.RoomMember;
import com.chamabosal.domain.room.repository.RoomMemberRepository;
import com.chamabosal.domain.room.repository.RoomRepository;
import com.chamabosal.domain.round.entity.Round;
import com.chamabosal.domain.round.repository.RoundParticipantRepository;
import com.chamabosal.domain.round.repository.RoundRepository;
import com.chamabosal.domain.store.entity.Item;
import com.chamabosal.domain.store.entity.ItemSlot;
import com.chamabosal.domain.store.entity.UserItem;
import com.chamabosal.domain.store.repository.ItemRepository;
import com.chamabosal.domain.store.repository.UserItemRepository;
import com.chamabosal.domain.user.entity.User;
import com.chamabosal.domain.user.repository.UserRepository;
import com.chamabosal.domain.vote.entity.Vote;
import com.chamabosal.domain.vote.repository.VoteRepository;
import com.chamabosal.global.security.jwt.JwtProvider;
import com.chamabosal.support.MutableClock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/**
 * 투표 탭 상태 (VOTE-05). 후보 목록(VOTE-02)은 `투표 중`·`투표권 없음` 상태의 candidates 필드로 함께 확인한다.
 * 공통 Given: 방 인원은 A, B, C, D 4명이다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(MutableClock.Config.class)
class VoteTabStatusTest {

    private static final LocalDate NOV_5 = LocalDate.of(2026, 11, 5);
    private static final LocalDateTime CERTIFIED_AT = NOV_5.atTime(20, 0);
    private static final LocalDateTime VOTE_STARTED_AT = NOV_5.plusDays(1).atTime(3, 0);

    @Autowired
    private RiceHistoryRepository riceHistoryRepository;

    @Autowired
    MockMvc mockMvc;

    @Autowired
    MutableClock clock;

    @Autowired
    JwtProvider jwtProvider;

    @Autowired
    VoteStartService voteStartService;

    @Autowired
    VoteCloseService voteCloseService;

    @Autowired
    UserRepository userRepository;

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

    @Autowired
    RoundParticipantRepository roundParticipantRepository;

    @Autowired
    CertificationRepository certificationRepository;

    @Autowired
    VoteRepository voteRepository;

    private int userSequence;
    private Room room;
    private User userA;
    private User userB;
    private User userC;
    private User userD;
    private RoomMember memberA;
    private RoomMember memberB;
    private RoomMember memberC;
    private RoomMember memberD;

    @BeforeEach
    void setUp() {
        clock.set(CERTIFIED_AT);
        room = roomRepository.save(Room.create("배달 끊기 방", RoomGoal.DELIVERY, 4, 300));
        userA = newUser();
        userB = newUser();
        userC = newUser();
        userD = newUser();
        memberA = addMember(userA, "민지", BosalColor.PINK);
        memberB = addMember(userB, "지수", BosalColor.PURPLE);
        memberC = addMember(userC, "하린", BosalColor.MINT);
        memberD = addMember(userD, "도윤", BosalColor.YELLOW);
    }

    @Autowired ItemRepository itemRepository;
    @Autowired RoomService roomService;
    @Autowired TapRepository tapRepository;
    @Autowired UserItemRepository userItemRepository;

    @AfterEach
    void tearDown() {
        userItemRepository.deleteAll();
        // 시작 때 넣은 카탈로그는 남기고 테스트가 만든 아이템만 지운다.
        itemRepository.findByCode("EQUIP_HAT").ifPresent(itemRepository::delete);
        tapRepository.deleteAll();
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

    @Test
    void 후보마다_착용_아이템_코드를_돌려준다() throws Exception {
        wear(userB, "EQUIP_HAT", ItemSlot.HAT);
        startVotingRound(memberA, memberB);

        tab(room.getId(), userA)
                .andExpect(jsonPath("$.data.candidates[?(@.memberId == " + memberB.getId() + ")].equippedItemCodes[*]")
                        .value(org.hamcrest.Matchers.contains("EQUIP_HAT")));
    }

    /** 착용한 아이템 하나를 준다. 시작 때 넣은 카탈로그와 겹치지 않는 코드를 쓴다. */
    private void wear(User user, String code, ItemSlot slot) {
        Item item = itemRepository.findByCode(code)
                .orElseGet(() -> itemRepository.save(Item.create(code, code, 100, slot, 99)));
        userItemRepository.save(UserItem.acquire(user, item));
    }

    @Test
    void VOTE_05_1_3명_미만() throws Exception {
        Room smallRoom = roomRepository.save(Room.create("혼자 방", RoomGoal.DELIVERY, 4, 300));
        User soloUser = newUser();
        roomMemberRepository.save(RoomMember.create(smallRoom, soloUser, "민지3", BosalColor.PINK, CERTIFIED_AT));

        tab(smallRoom.getId(), soloUser).andExpect(jsonPath("$.data.status").value("UNDER_THREE"))
                .andExpect(jsonPath("$.data.memberCount").value(1))
                .andExpect(jsonPath("$.data.memberLimit").value(4));
    }

    @Test
    void VOTE_05_2_첫_투표_전() throws Exception {
        tab(room.getId(), userA).andExpect(jsonPath("$.data.status").value("BEFORE_FIRST_VOTE"))
                .andExpect(jsonPath("$.data.uncertifiedCount").value(3));
    }

    @Test
    void VOTE_05_3_명단_밖() throws Exception {
        Round round = startVotingRound(memberA, memberB, memberC);
        User userE = newUser();
        roomMemberRepository.save(RoomMember.create(room, userE, "은우", BosalColor.PINK, VOTE_STARTED_AT.plusHours(1)));

        tab(room.getId(), userE).andExpect(jsonPath("$.data.status").value("NOT_IN_LIST"))
                .andExpect(jsonPath("$.data.roundId").value(round.getId()));
    }

    @Test
    void VOTE_05_4_투표_중() throws Exception {
        startVotingRound(memberA, memberB, memberC, memberD);

        tab(room.getId(), userA).andExpect(jsonPath("$.data.status").value("VOTING"))
                .andExpect(jsonPath("$.data.candidates.length()").value(3));
    }

    @Test
    void VOTE_05_5_투표_완료() throws Exception {
        Round round = startVotingRound(memberA, memberB, memberC, memberD);
        vote(round, memberA, memberB);

        tab(room.getId(), userA).andExpect(jsonPath("$.data.status").value("VOTED"))
                .andExpect(jsonPath("$.data.candidates").doesNotExist());
    }

    @Test
    void VOTE_05_6_결과_미확인() throws Exception {
        Round round = startVotingRound(memberA, memberB, memberC, memberD);
        vote(round, memberA, memberB);
        vote(round, memberB, memberA);
        vote(round, memberC, memberA);
        vote(round, memberD, memberA);
        voteCloseService.closeIfAllVoted(room.getId(), VOTE_STARTED_AT.plusHours(1));

        tab(room.getId(), userA).andExpect(jsonPath("$.data.status").value("RESULT_UNSEEN"))
                .andExpect(jsonPath("$.data.roundId").value(round.getId()));
    }

    @Test
    void VOTE_05_7_결과_확인_후() throws Exception {
        Round round = startVotingRound(memberA, memberB, memberC, memberD);
        vote(round, memberA, memberB);
        vote(round, memberB, memberA);
        vote(round, memberC, memberA);
        vote(round, memberD, memberA);
        voteCloseService.closeIfAllVoted(room.getId(), VOTE_STARTED_AT.plusHours(1));
        memberA.fillSeenResult(round.getRoundDate());
        roomMemberRepository.save(memberA);

        tab(room.getId(), userA).andExpect(jsonPath("$.data.status").value("RESULT_SEEN"));
    }

    @Test
    void VOTE_05_8_투표권_없음() throws Exception {
        startVotingRound(memberA, memberB, memberC);
        // D는 인증하지 않았다.

        tab(room.getId(), userD).andExpect(jsonPath("$.data.status").value("NO_VOTING_RIGHT"))
                .andExpect(jsonPath("$.data.candidates").isArray());
    }

    @Test
    void VOTE_05_9_투표_완료_화면_데이터() throws Exception {
        Round round = startVotingRound(memberA, memberB, memberC, memberD);
        vote(round, memberA, memberB);
        vote(round, memberB, memberA);

        tab(room.getId(), userA).andExpect(jsonPath("$.data.status").value("VOTED"))
                .andExpect(jsonPath("$.data.votedCount").value(2))
                .andExpect(jsonPath("$.data.voterCount").value(4))
                .andExpect(jsonPath("$.data.candidates").doesNotExist());
    }

    @Test
    void VOTE_05_10_투표권_없음_화면_데이터() throws Exception {
        startVotingRound(memberA, memberB, memberC);
        // D는 인증하지 않았다.

        tab(room.getId(), userD).andExpect(jsonPath("$.data.status").value("NO_VOTING_RIGHT"))
                // 투표권 있는 상태(VOTE_02_1)는 본인을 뺀 3명인데, 여기서는 나도 포함해 4명이다 (VOTE-05-10).
                .andExpect(jsonPath("$.data.candidates.length()").value(4));
    }

    @Test
    void VOTE_02_1_본인_제외() throws Exception {
        startVotingRound(memberA, memberB, memberC, memberD);

        tab(room.getId(), userA).andExpect(jsonPath("$.data.candidates.length()").value(3))
                .andExpect(jsonPath("$.data.candidates[?(@.memberId == " + memberA.getId() + ")]").isEmpty());
    }

    @Test
    void VOTE_02_5_기존_멤버_미인증은_후보() throws Exception {
        startVotingRound(memberA, memberC, memberD);
        // B는 인증하지 않았다.

        tab(room.getId(), userA)
                .andExpect(jsonPath("$.data.candidates[?(@.memberId == " + memberB.getId() + ")]").isNotEmpty());
    }

    @Test
    void VOTE_02_6_오늘_들어온_미인증_멤버_제외() throws Exception {
        RoomMember newcomer = roomMemberRepository.save(
                RoomMember.create(room, newUser(), "새온", BosalColor.PINK, CERTIFIED_AT));
        startVotingRound(memberA, memberB, memberC);
        // newcomer는 오늘 들어왔고 인증하지 않았다.

        tab(room.getId(), userA)
                .andExpect(jsonPath("$.data.candidates[?(@.memberId == " + newcomer.getId() + ")]").isEmpty());
    }

    @Test
    void VOTE_02_7_시작_후_참여자_제외() throws Exception {
        startVotingRound(memberA, memberB, memberC, memberD);
        User userE = newUser();
        RoomMember memberE = roomMemberRepository.save(
                RoomMember.create(room, userE, "은우", BosalColor.PINK, VOTE_STARTED_AT.plusHours(1)));

        tab(room.getId(), userA)
                .andExpect(jsonPath("$.data.candidates[?(@.memberId == " + memberE.getId() + ")]").isEmpty());
    }

    @Test
    void VOTE_02_8_후보가_한_명뿐이면_그_한_명과_모두_보살을_고를_수_있다() throws Exception {
        // C·D는 오늘 들어와 인증하지 않아 후보에서 빠진다 (VOTE-02-6). 명단은 3명 이상이라 투표는 열린다.
        RoomMember newC = roomMemberRepository.save(RoomMember.create(room, newUser(), "새온", BosalColor.MINT, CERTIFIED_AT));
        roomMemberRepository.save(RoomMember.create(room, newUser(), "새봄", BosalColor.YELLOW, CERTIFIED_AT));
        memberC.leave(CERTIFIED_AT, LeaveType.LEFT);
        memberD.leave(CERTIFIED_AT, LeaveType.LEFT);
        roomMemberRepository.saveAll(List.of(memberC, memberD));
        Round round = startVotingRound(memberA, memberB);

        tab(room.getId(), userA)
                .andExpect(jsonPath("$.data.candidates.length()").value(1))
                .andExpect(jsonPath("$.data.candidates[0].memberId").value(memberB.getId()))
                .andExpect(jsonPath("$.data.candidates[?(@.memberId == " + newC.getId() + ")]").isEmpty());
        mockMvc.perform(post("/api/rounds/{roundId}/votes", round.getId())
                        .header("Authorization", "Bearer " + jwtProvider.createAccessToken(userA.getId()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"targetType\": \"ALL_BOSAL\"}"))
                .andExpect(status().isCreated());
    }

    @Test
    void VOTE_02_10_투표_중에_나간_멤버도_후보로_남는다() throws Exception {
        startVotingRound(memberA, memberB, memberC, memberD);
        clock.set(VOTE_STARTED_AT.plusHours(1));

        roomService.leave(userB.getId(), room.getId());

        tab(room.getId(), userA)
                .andExpect(jsonPath("$.data.status").value("VOTING"))
                .andExpect(jsonPath("$.data.candidates[?(@.memberId == " + memberB.getId() + ")]").isNotEmpty());
    }

    @Test
    void VOTE_05_11_투표_중_3명_미만이_돼도_투표_탭은_투표_중이다() throws Exception {
        startVotingRound(memberA, memberB, memberC, memberD);
        clock.set(VOTE_STARTED_AT.plusHours(1));

        roomService.leave(userC.getId(), room.getId());
        roomService.leave(userD.getId(), room.getId());

        tab(room.getId(), userA).andExpect(jsonPath("$.data.status").value("VOTING"));
    }

    // ---------- 결과 뒤 이마 톡톡·목탁 수행 (VOTE-05 5번) ----------

    @Test
    void VOTE_05_12_결과를_보고_톡톡할_수_있으면_이마_톡톡이다() throws Exception {
        Round round = pickedB();
        seeResult(userA, round);

        tab(room.getId(), userA)
                .andExpect(jsonPath("$.data.status").value("TAP"))
                .andExpect(jsonPath("$.data.roundId").value(round.getId()))
                .andExpect(jsonPath("$.data.tapClosesAt").value("2026-11-06T20:00:00"));
    }

    @Test
    void VOTE_05_13_톡톡을_보낸_뒤에는_결과_확인_후다() throws Exception {
        Round round = pickedB();
        seeResult(userA, round);
        sendTap(userA, round, 3);

        tab(room.getId(), userA).andExpect(jsonPath("$.data.status").value("RESULT_SEEN"));
    }

    @Test
    void VOTE_05_14_지목된_사람은_톡톡_전달_전까지_결과_확인_후다() throws Exception {
        Round round = pickedB();
        seeResult(userB, round);
        sendTap(userA, round, 5);

        clock.set(LocalDateTime.of(2026, 11, 6, 19, 59));
        tab(room.getId(), userB).andExpect(jsonPath("$.data.status").value("RESULT_SEEN"));
    }

    @Test
    void VOTE_05_15_톡톡이_전달되면_지목된_사람은_목탁_수행이고_마치면_결과_확인_후다() throws Exception {
        Round round = pickedB();
        seeResult(userB, round);
        sendTap(userA, round, 5);
        sendTap(userC, round, 3);

        clock.set(LocalDateTime.of(2026, 11, 6, 20, 0));
        tab(room.getId(), userB)
                .andExpect(jsonPath("$.data.status").value("MOKTAK"))
                .andExpect(jsonPath("$.data.moktakCount").value(8));
        mockMvc.perform(post("/api/rounds/{roundId}/moktak", round.getId())
                        .header("Authorization", "Bearer " + jwtProvider.createAccessToken(userB.getId()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"count\": 8}"))
                .andExpect(status().isCreated());
        tab(room.getId(), userB).andExpect(jsonPath("$.data.status").value("RESULT_SEEN"));
    }

    @Test
    void 결과_뒤_인원이_3명_미만이_돼도_톡톡을_먼저_보여준다() throws Exception {
        Round round = pickedB();
        seeResult(userA, round);
        roomService.leave(userC.getId(), room.getId());
        roomService.leave(userD.getId(), room.getId());

        tab(room.getId(), userA).andExpect(jsonPath("$.data.status").value("TAP"));
    }

    @Test
    void 종료하기로_목탁_수행을_끝내면_투표_탭은_결과_확인_후다() throws Exception {
        Round round = pickedB();
        seeResult(userB, round);
        sendTap(userA, round, 5);
        sendTap(userC, round, 3);
        clock.set(LocalDateTime.of(2026, 11, 6, 20, 0));

        mockMvc.perform(post("/api/rounds/{roundId}/moktak", round.getId())
                        .header("Authorization", "Bearer " + jwtProvider.createAccessToken(userB.getId()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"count\": 3}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.riceEarned").value(0));

        tab(room.getId(), userB).andExpect(jsonPath("$.data.status").value("RESULT_SEEN"));
    }

    /** 혼자 남은 방은 공양미가 쌓이지 않아 목탁 수행을 띄우지 않는다. 띄우면 마쳐도 완료 기록이 남지 않아 계속 남는다 (코드리뷰 반영). */
    @Test
    void 혼자_남은_방에서는_목탁_수행을_띄우지_않는다() throws Exception {
        Round round = pickedB();
        seeResult(userB, round);
        sendTap(userA, round, 5);
        roomService.leave(userA.getId(), room.getId());
        roomService.leave(userC.getId(), room.getId());
        roomService.leave(userD.getId(), room.getId());

        clock.set(LocalDateTime.of(2026, 11, 6, 20, 0));
        tab(room.getId(), userB).andExpect(jsonPath("$.data.status").value("UNDER_THREE"));
    }

    // ---------- 투표 없음 (VOTE-05 6번) ----------

    @Test
    void VOTE_05_16_투표_없음으로_확정된_다음_날은_투표_없음이다() throws Exception {
        Round picked = pickedB();
        seeResult(userA, picked);
        sendTap(userA, picked, 3);
        // 11/6 회차는 콕만 있어 행이 생겼고, 인증 0명이라 11/7 03:00에 투표 없음으로 확정된다.
        Round noVote = roundRepository.save(Round.create(room, NOV_5.plusDays(1)));
        voteStartService.startAtDeadline(room.getId(), noVote.getId(), LocalDateTime.of(2026, 11, 7, 3, 0));

        clock.set(LocalDateTime.of(2026, 11, 7, 10, 0));
        tab(room.getId(), userA)
                .andExpect(jsonPath("$.data.status").value("NO_VOTE"))
                .andExpect(jsonPath("$.data.roundId").value(noVote.getId()))
                .andExpect(jsonPath("$.data.roundDate").value("2026-11-06"));
    }

    @Test
    void VOTE_05_17_인증도_콕도_없던_다음_날은_회차_ID_없이_투표_없음이다() throws Exception {
        Round picked = pickedB();
        seeResult(userA, picked);
        sendTap(userA, picked, 3);
        // 11/6에는 인증도 콕도 없어 회차 행이 없다.

        clock.set(LocalDateTime.of(2026, 11, 7, 10, 0));
        tab(room.getId(), userA)
                .andExpect(jsonPath("$.data.status").value("NO_VOTE"))
                .andExpect(jsonPath("$.data.roundId").doesNotExist())
                .andExpect(jsonPath("$.data.roundDate").value("2026-11-06"));
    }

    @Test
    void VOTE_05_18_들어온_날은_전날_투표_없음을_보지_않는다() throws Exception {
        // 11/5에는 인증도 콕도 없었다. A는 10/26부터 있었다 (setUp).
        User userE = newUser();
        roomMemberRepository.save(RoomMember.create(room, userE, "은우", BosalColor.PINK, LocalDateTime.of(2026, 11, 6, 9, 0)));

        clock.set(LocalDateTime.of(2026, 11, 6, 10, 0));
        tab(room.getId(), userA).andExpect(jsonPath("$.data.status").value("NO_VOTE"))
                .andExpect(jsonPath("$.data.roundDate").value("2026-11-05"));
        tab(room.getId(), userE).andExpect(jsonPath("$.data.status").value("BEFORE_FIRST_VOTE"));
    }

    @Test
    void VOTE_05_19_남은_목탁_수행은_투표_없음보다_먼저다() throws Exception {
        Round picked = pickedB();
        seeResult(userB, picked);
        sendTap(userA, picked, 5);
        // 11/6에는 인증이 없었다.

        clock.set(LocalDateTime.of(2026, 11, 7, 10, 0));
        tab(room.getId(), userB).andExpect(jsonPath("$.data.status").value("MOKTAK"));
    }

    @Test
    void VOTE_05_20_오늘_회차가_이미_투표_없음이면_투표_없음이다() throws Exception {
        Room pairRoom = roomRepository.save(Room.create("둘 방", RoomGoal.DELIVERY, 4, 300));
        User userX = newUser();
        RoomMember memberX = roomMemberRepository.save(
                RoomMember.create(pairRoom, userX, "가온", BosalColor.PINK, NOV_5.minusDays(10).atStartOfDay()));
        RoomMember memberY = roomMemberRepository.save(
                RoomMember.create(pairRoom, newUser(), "나래", BosalColor.MINT, NOV_5.minusDays(10).atStartOfDay()));
        Round round = roundRepository.save(Round.create(pairRoom, NOV_5));
        for (RoomMember member : List.of(memberX, memberY)) {
            certificationRepository.save(Certification.create(
                    round, member, CertificationResult.ENDURED, "집밥 해먹었어요", null, CERTIFIED_AT));
        }
        // 둘뿐이라 전원 인증해도 투표 없이 투표 없음이 된다.
        voteStartService.startAtDeadline(pairRoom.getId(), round.getId(), CERTIFIED_AT);
        roomMemberRepository.save(RoomMember.create(pairRoom, newUser(), "다온", BosalColor.YELLOW, NOV_5.atTime(20, 30)));

        clock.set(NOV_5.atTime(21, 0));
        tab(pairRoom.getId(), userX)
                .andExpect(jsonPath("$.data.status").value("NO_VOTE"))
                .andExpect(jsonPath("$.data.roundId").value(round.getId()))
                .andExpect(jsonPath("$.data.roundDate").value("2026-11-05"));
    }

    @Test
    void VOTE_05_21_03시_배치_전에는_투표_없음이_아니다() throws Exception {
        Round round = roundRepository.save(Round.create(room, NOV_5));
        certificationRepository.save(Certification.create(
                round, memberA, CertificationResult.ENDURED, "집밥 해먹었어요", null, CERTIFIED_AT));

        clock.set(LocalDateTime.of(2026, 11, 6, 3, 0, 30));
        tab(room.getId(), userB).andExpect(jsonPath("$.data.status").value("BEFORE_FIRST_VOTE"));
    }

    @Test
    void VOTE_05_22_오늘_회차가_발표됐으면_전날_투표_없음을_보지_않는다() throws Exception {
        // 11/5에는 인증이 없었다. 11/6 회차는 전원 인증으로 21:00에 열려 21:30에 B가 지목됐다.
        LocalDate nov6 = NOV_5.plusDays(1);
        Round round = roundRepository.save(Round.create(room, nov6));
        for (RoomMember member : List.of(memberA, memberB, memberC, memberD)) {
            certificationRepository.save(Certification.create(
                    round, member, CertificationResult.ENDURED, "집밥 해먹었어요", null, nov6.atTime(20, 0)));
        }
        voteStartService.startAtDeadline(room.getId(), round.getId(), nov6.atTime(21, 0));
        vote(round, memberA, memberB);
        vote(round, memberC, memberB);
        vote(round, memberD, memberB);
        vote(round, memberB, memberA);
        voteCloseService.closeIfAllVoted(room.getId(), nov6.atTime(21, 30));
        seeResult(userB, round);

        clock.set(nov6.atTime(22, 0));
        tab(room.getId(), userB).andExpect(jsonPath("$.data.status").value("RESULT_SEEN"));
    }

    @Test
    void VOTE_05_23_03시_전에_들어오면_그_회차부터_본다() throws Exception {
        // 11/5에는 인증도 콕도 없었다. E는 11/6 01:30에 들어와 11/5 회차에 속한다.
        User userE = newUser();
        roomMemberRepository.save(RoomMember.create(room, userE, "은우", BosalColor.PINK, LocalDateTime.of(2026, 11, 6, 1, 30)));

        clock.set(LocalDateTime.of(2026, 11, 6, 10, 0));
        tab(room.getId(), userE).andExpect(jsonPath("$.data.status").value("NO_VOTE"))
                .andExpect(jsonPath("$.data.roundDate").value("2026-11-05"));
    }

    /** A·C·D가 B를, B가 A를 골라 11/6 15:00에 B가 지목된 11/5 회차. 지금은 11/6 16:00이다. */
    private Round pickedB() {
        Round round = startVotingRound(memberA, memberB, memberC, memberD);
        vote(round, memberA, memberB);
        vote(round, memberC, memberB);
        vote(round, memberD, memberB);
        vote(round, memberB, memberA);
        voteCloseService.closeAtDeadline(room.getId(), round.getId(), LocalDateTime.of(2026, 11, 6, 15, 0));
        clock.set(LocalDateTime.of(2026, 11, 6, 16, 0));
        return roundRepository.findById(round.getId()).orElseThrow();
    }

    private void seeResult(User user, Round round) {
        roomService.markResultSeen(user.getId(), room.getId(), new RoomSeenResultUpdateRequest(round.getId()));
    }

    private void sendTap(User user, Round round, int count) throws Exception {
        mockMvc.perform(post("/api/rounds/{roundId}/taps", round.getId())
                        .header("Authorization", "Bearer " + jwtProvider.createAccessToken(user.getId()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"count\": " + count + "}"))
                .andExpect(status().isCreated());
    }

    private org.springframework.test.web.servlet.ResultActions tab(Long roomId, User user) throws Exception {
        return mockMvc.perform(get("/api/rooms/{roomId}/vote", roomId)
                        .header("Authorization", "Bearer " + jwtProvider.createAccessToken(user.getId())))
                .andExpect(status().isOk());
    }

    private Round startVotingRound(RoomMember... certifiedMembers) {
        Round round = roundRepository.save(Round.create(room, NOV_5));
        for (RoomMember member : certifiedMembers) {
            certificationRepository.save(Certification.create(
                    round, member, CertificationResult.ENDURED, "집밥 해먹었어요", null, CERTIFIED_AT));
        }
        voteStartService.startAtDeadline(room.getId(), round.getId(), VOTE_STARTED_AT);
        return roundRepository.findById(round.getId()).orElseThrow();
    }

    private void vote(Round round, RoomMember voter, RoomMember target) {
        voteRepository.save(Vote.create(roundRepository.findById(round.getId()).orElseThrow(), voter, target));
    }

    private User newUser() {
        return userRepository.save(User.create("anon-tab-" + userSequence++));
    }

    private RoomMember addMember(User user, String nickname, BosalColor color) {
        return roomMemberRepository.save(RoomMember.create(room, user, nickname, color, NOV_5.minusDays(10).atStartOfDay()));
    }
}
