package com.chamabosal.domain.result.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.chamabosal.domain.certification.entity.Certification;
import com.chamabosal.domain.certification.entity.CertificationResult;
import com.chamabosal.domain.certification.repository.CertificationRepository;
import com.chamabosal.domain.defense.entity.DefenseTicket;
import com.chamabosal.domain.defense.repository.DefenseTicketRepository;
import com.chamabosal.domain.rice.entity.RiceReason;
import com.chamabosal.domain.rice.repository.RiceHistoryRepository;
import com.chamabosal.domain.room.entity.BosalColor;
import com.chamabosal.domain.room.entity.Room;
import com.chamabosal.domain.room.entity.RoomGoal;
import com.chamabosal.domain.room.entity.RoomMember;
import com.chamabosal.domain.room.repository.RoomMemberRepository;
import com.chamabosal.domain.room.repository.RoomRepository;
import com.chamabosal.domain.room.service.RoomService;
import com.chamabosal.domain.round.entity.Round;
import com.chamabosal.domain.round.entity.RoundResultType;
import com.chamabosal.domain.round.entity.RoundStatus;
import com.chamabosal.domain.round.repository.RoundParticipantRepository;
import com.chamabosal.domain.round.repository.RoundRepository;
import com.chamabosal.domain.tap.entity.Tap;
import com.chamabosal.domain.tap.repository.TapRepository;
import com.chamabosal.domain.user.entity.User;
import com.chamabosal.domain.user.repository.UserRepository;
import com.chamabosal.domain.vote.entity.Vote;
import com.chamabosal.domain.vote.repository.VoteRepository;
import com.chamabosal.domain.vote.service.VoteCloseService;
import com.chamabosal.domain.vote.service.VoteStartService;
import com.chamabosal.global.security.jwt.JwtProvider;
import com.chamabosal.support.MutableClock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/**
 * 결과 화면(RESULT-02), 이마 톡톡(RESULT-03), 결과 확인 저장(ROOM-06). 공통 Given: 방 인원은 A, B, C, D 4명이다.
 * 발표된 회차는 실제 흐름(인증 → 다음 날 03:00 투표 시작 → 표 → 다음 날 15:00 마감)으로 만든다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(MutableClock.Config.class)
class ResultTapTest {

    private static final LocalDate NOV_2 = LocalDate.of(2026, 11, 2);
    private static final LocalDate NOV_3 = LocalDate.of(2026, 11, 3);
    private static final LocalDate NOV_4 = LocalDate.of(2026, 11, 4);
    private static final LocalDate NOV_5 = LocalDate.of(2026, 11, 5);
    private static final LocalDateTime AFTER_CLOSE = NOV_4.plusDays(1).atTime(16, 0);

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
    RoomService roomService;

    @Autowired
    UserRepository userRepository;

    @Autowired
    RoomRepository roomRepository;

    @Autowired
    RoomMemberRepository roomMemberRepository;

    @Autowired
    RoundRepository roundRepository;

    @Autowired
    RoundParticipantRepository roundParticipantRepository;

    @Autowired
    CertificationRepository certificationRepository;

    @Autowired
    VoteRepository voteRepository;

    @Autowired
    TapRepository tapRepository;

    @Autowired
    RiceHistoryRepository riceHistoryRepository;

    @Autowired
    DefenseTicketRepository defenseTicketRepository;

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
        clock.set(AFTER_CLOSE);
        room = newRoom();
        userA = newUser();
        userB = newUser();
        userC = newUser();
        userD = newUser();
        memberA = addMember(room, userA, "민지", BosalColor.PINK);
        memberB = addMember(room, userB, "지수", BosalColor.PURPLE);
        memberC = addMember(room, userC, "하린", BosalColor.MINT);
        memberD = addMember(room, userD, "도윤", BosalColor.YELLOW);
    }

    @AfterEach
    void tearDown() {
        defenseTicketRepository.deleteAll();
        riceHistoryRepository.deleteAll();
        tapRepository.deleteAll();
        voteRepository.deleteAll();
        roundParticipantRepository.deleteAll();
        certificationRepository.deleteAll();
        roundRepository.deleteAll();
        roomMemberRepository.deleteAll();
        roomRepository.deleteAll();
        userRepository.deleteAll();
    }

    // ---------- RESULT-02 결과 화면 ----------

    @Test
    void RESULT_02_1_안_본_결과가_여럿이면_가장_최근_하나만_보이고_확인하면_이전_회차도_본_것이다() throws Exception {
        announced(NOV_2, picks(memberB, memberA, memberC, memberA, memberD, memberA, memberA, memberB));
        announced(NOV_3, picks(memberB, memberA, memberC, memberA, memberD, memberA, memberA, memberB));
        Round wednesday = announced(NOV_4, picks(memberB, memberA, memberC, memberA, memberD, memberA, memberA, memberB));

        voteTab(userB).andExpect(jsonPath("$.data.status").value("RESULT_UNSEEN"))
                .andExpect(jsonPath("$.data.roundId").value(wednesday.getId()));

        seenResult(userB, room, wednesday.getId()).andExpect(status().isOk());

        voteTab(userB).andExpect(jsonPath("$.data.status").value("RESULT_SEEN"));
        assertThat(reload(memberB).getSeenResultRoundDate()).isEqualTo(NOV_4);
    }

    @Test
    void RESULT_02_2_지목된_사람을_뺀_멤버는_투표하지_않았어도_톡톡_기회가_있다() throws Exception {
        // B는 투표하지 않는다. C, D가 A를 골라 A가 지목된다.
        Round round = announced(NOV_4, picks(memberC, memberA, memberD, memberA, memberA, memberC));

        for (User user : new User[] {userB, userC, userD}) {
            result(user, round).andExpect(jsonPath("$.data.resultType").value("PICKED"))
                    .andExpect(jsonPath("$.data.canTap").value(true));
        }
        result(userA, round).andExpect(jsonPath("$.data.isMePicked").value(true))
                .andExpect(jsonPath("$.data.canTap").value(false));
    }

    @Test
    void RESULT_02_3_모두_보살_모두_무사_방어된_결과에는_톡톡_기회가_없다() throws Exception {
        Round allBosal = announced(NOV_2, picks(memberA, null, memberB, null, memberC, null, memberD, null));
        Round allSafe = announced(NOV_3, picks(memberA, memberB, memberB, memberA, memberC, memberA, memberD, memberB));
        Round defended = announced(NOV_4, picks(memberB, memberA, memberC, memberA, memberD, memberA, memberA, memberB));
        defend(defended);

        result(userC, allBosal).andExpect(jsonPath("$.data.resultType").value("ALL_BOSAL"))
                .andExpect(jsonPath("$.data.canTap").value(false));
        result(userC, allSafe).andExpect(jsonPath("$.data.resultType").value("ALL_SAFE"))
                .andExpect(jsonPath("$.data.canTap").value(false));
        result(userC, defended).andExpect(jsonPath("$.data.resultType").value("DEFENDED"))
                .andExpect(jsonPath("$.data.canTap").value(false));
    }

    @Test
    void RESULT_02_4_지목된_사람만_방어_마감_시각을_받는다() throws Exception {
        Round round = announced(NOV_2, picks(memberB, memberA, memberC, memberA, memberD, memberA, memberA, memberB));
        defenseTicketRepository.save(DefenseTicket.purchase(userA, NOV_2.atStartOfDay()));
        defenseTicketRepository.save(DefenseTicket.purchase(userA, NOV_2.atStartOfDay()));

        clock.set(NOV_3.atTime(8, 0));
        result(userA, round)
                .andExpect(jsonPath("$.data.defenseUsableUntil").value("2026-11-03T08:02:00"))
                .andExpect(jsonPath("$.data.defenseTicketCount").value(2))
                .andExpect(jsonPath("$.data.canUseDefense").value(true));
        result(userB, round)
                .andExpect(jsonPath("$.data.defenseUsableUntil").doesNotExist())
                .andExpect(jsonPath("$.data.defenseTicketCount").doesNotExist())
                .andExpect(jsonPath("$.data.canUseDefense").value(false));

        // 다시 열어도 처음 조회 시각은 그대로다.
        clock.set(NOV_3.atTime(9, 0));
        result(userA, round).andExpect(jsonPath("$.data.defenseUsableUntil").value("2026-11-03T08:02:00"));
    }

    @Test
    void RESULT_02_5_다음_투표가_열린_뒤_처음_열면_톡톡_시간이_지났다() throws Exception {
        Round round = announced(NOV_3, picks(memberB, memberA, memberC, memberA, memberD, memberA, memberA, memberB));
        startVoting(NOV_4);

        result(userC, round)
                .andExpect(jsonPath("$.data.resultType").value("PICKED"))
                .andExpect(jsonPath("$.data.isTapExpired").value(true))
                .andExpect(jsonPath("$.data.canTap").value(false));
    }

    @Test
    void 결과_화면은_지목된_사람의_인증과_표를_받은_멤버의_득표_수만_보여준다() throws Exception {
        Round round = announced(NOV_4, picks(memberB, memberA, memberC, memberA, memberD, null, memberA, memberB));

        result(userC, round)
                .andExpect(jsonPath("$.data.picked.memberId").value(memberA.getId()))
                .andExpect(jsonPath("$.data.picked.certificationResult").value("FAILED"))
                .andExpect(jsonPath("$.data.picked.spentAmount").value(23000))
                .andExpect(jsonPath("$.data.voteCounts.length()").value(2))
                .andExpect(jsonPath("$.data.voteCounts[0].memberId").value(memberA.getId()))
                .andExpect(jsonPath("$.data.voteCounts[0].count").value(2))
                .andExpect(jsonPath("$.data.voteCounts[1].memberId").value(memberB.getId()))
                .andExpect(jsonPath("$.data.allBosalCount").value(1));
    }

    @Test
    void 발표_전_회차의_결과는_볼_수_없다() throws Exception {
        Round voting = startVoting(NOV_4);

        result(userB, voting).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("RESULT_001"));
    }

    // ---------- RESULT-03 이마 톡톡 ----------

    @Test
    void RESULT_03_1_999번까지_보낼_수_있고_재시도해도_저장된_횟수는_그대로다() throws Exception {
        Round round = pickedA();
        tap(userC, round, 1000).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("COMMON_002"));
        tap(userB, round, 999).andExpect(status().isCreated());

        tap(userB, round, 999).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("TAP_003"));
        assertThat(tapOf(round, memberB).getCount()).isEqualTo(999);
    }

    @Test
    void RESULT_03_2_중간에_나가_4번만_보냈으면_더_칠_수_없다() throws Exception {
        Round round = pickedA();
        tap(userB, round, 4).andExpect(status().isCreated());

        tap(userB, round, 6).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("TAP_003"));
        assertThat(tapOf(round, memberB).getCount()).isEqualTo(4);
    }

    @Test
    void RESULT_03_3_건너뛰면_0회로_끝나고_다시_칠_수_없다() throws Exception {
        Round round = pickedA();
        tap(userB, round, 0).andExpect(status().isCreated());

        tap(userB, round, 3).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("TAP_003"));
        assertThat(tapOf(round, memberB).getCount()).isZero();
    }

    @Test
    void RESULT_03_4_다음_투표가_열리면_보낼_수_없다() throws Exception {
        Round round = pickedA();
        startVoting(NOV_5);

        tap(userB, round, 5).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("TAP_004"));
    }

    @Test
    void RESULT_03_5_지목된_본인과_방을_나간_사람은_보낼_수_없다() throws Exception {
        Round round = pickedA();
        tap(userA, round, 5).andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("TAP_002"));

        roomService.leave(userD.getId(), room.getId());
        tap(userD, round, 5).andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("ROOM_002"));
    }

    @Test
    void RESULT_03_6_투표_시작_뒤_들어온_멤버도_보낼_수_있다() throws Exception {
        Round round = pickedA();
        User userE = newUser();
        addMember(room, userE, "은우", BosalColor.PINK, AFTER_CLOSE.minusHours(1));

        tap(userE, round, 5).andExpect(status().isCreated());
    }

    @Test
    void RESULT_03_7_받은_톡톡은_재생한_뒤_다시_나오지_않는다() throws Exception {
        Round round = pickedA();
        tap(userB, round, 5).andExpect(status().isCreated());

        received(userA, round).andExpect(jsonPath("$.data.taps.length()").value(1))
                .andExpect(jsonPath("$.data.taps[0].nickname").value("지수"))
                .andExpect(jsonPath("$.data.taps[0].count").value(5));
        played(userA, round).andExpect(status().isOk());

        received(userA, round).andExpect(jsonPath("$.data.taps.length()").value(0));
    }

    @Test
    void RESULT_03_8_재생_완료_뒤_조회하면_빈_목록이다() throws Exception {
        Round round = pickedA();
        tap(userB, round, 5).andExpect(status().isCreated());
        played(userA, round).andExpect(status().isOk());
        played(userA, round).andExpect(status().isOk());

        received(userA, round).andExpect(jsonPath("$.data.taps").isEmpty());
    }

    @Test
    void RESULT_03_9_재생_뒤에_도착한_톡톡만_나온다() throws Exception {
        Round round = pickedA();
        tap(userB, round, 5).andExpect(status().isCreated());
        played(userA, round).andExpect(status().isOk());

        tap(userC, round, 3).andExpect(status().isCreated());

        received(userA, round).andExpect(jsonPath("$.data.taps.length()").value(1))
                .andExpect(jsonPath("$.data.taps[0].nickname").value("하린"));
    }

    @Test
    void RESULT_03_10_방어_시간_중에도_보낼_수_있다() throws Exception {
        Round round = pickedA();
        result(userA, round).andExpect(status().isOk());

        clock.set(AFTER_CLOSE.plusMinutes(1));
        tap(userB, round, 5).andExpect(status().isCreated());
    }

    @Test
    void RESULT_03_11_방어된_결과에는_보낼_수_없고_받은_톡톡도_비어_있다() throws Exception {
        Round round = pickedA();
        tap(userB, round, 5).andExpect(status().isCreated());
        defend(round);

        tap(userC, round, 5).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("TAP_001"));
        received(userA, round).andExpect(jsonPath("$.data.taps").isEmpty());
    }

    @Test
    void RESULT_03_12_톡톡을_1번_이상_보내면_5석을_받는다() throws Exception {
        Round round = pickedA();

        tap(userB, round, 3).andExpect(status().isCreated()).andExpect(jsonPath("$.data.riceEarned").value(5));

        assertThat(riceHistoryRepository.findAll()).singleElement()
                .satisfies(history -> {
                    assertThat(history.getReason()).isEqualTo(RiceReason.TAP);
                    assertThat(history.getDelta()).isEqualTo(5);
                });
    }

    @Test
    void RESULT_03_13_건너뛰면_공양미가_없다() throws Exception {
        Round round = pickedA();

        tap(userB, round, 0).andExpect(status().isCreated()).andExpect(jsonPath("$.data.riceEarned").value(0));

        assertThat(riceHistoryRepository.findAll()).isEmpty();
    }

    @Test
    void RESULT_03_14_15시에_자동_마감된_결과에도_보낼_수_있다() throws Exception {
        // A, B는 투표하지 않은 채 15:00에 자동 마감되고, C·D의 표로 A가 지목된다.
        Round round = announced(NOV_4, picks(memberC, memberA, memberD, memberA));
        assertThat(round.getResultType()).isEqualTo(RoundResultType.PICKED);

        tap(userB, round, 5).andExpect(status().isCreated());
    }

    @Test
    void 지목된_사람이_아니면_받은_톡톡을_볼_수_없다() throws Exception {
        Round round = pickedA();

        received(userB, round).andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("TAP_005"));
        played(userB, round).andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("TAP_005"));
    }

    @Test
    void RICE_01_7_톡톡_완료_보상() throws Exception {
        Round round = pickedA();

        tap(userB, round, 3).andExpect(jsonPath("$.data.riceEarned").value(5));
    }

    @Test
    void RICE_01_8_톡톡_건너뛰기는_보상_없음() throws Exception {
        Round round = pickedA();

        tap(userB, round, 0).andExpect(jsonPath("$.data.riceEarned").value(0));
    }

    // ---------- ROOM-06 결과 확인 저장 ----------

    @Test
    void ROOM_06_1_결과를_확인하면_안_본_결과가_비어_있다() throws Exception {
        Round round = pickedA();
        voteTab(userB).andExpect(jsonPath("$.data.status").value("RESULT_UNSEEN"));

        seenResult(userB, room, round.getId()).andExpect(status().isOk());

        voteTab(userB).andExpect(jsonPath("$.data.status").value("RESULT_SEEN"));
    }

    @Test
    void ROOM_06_2_이전_회차를_확인해도_저장값은_그대로다() throws Exception {
        Round earlier = announced(NOV_3, picks(memberB, memberA, memberC, memberA, memberD, memberA, memberA, memberB));
        Round later = announced(NOV_4, picks(memberB, memberA, memberC, memberA, memberD, memberA, memberA, memberB));
        seenResult(userB, room, later.getId()).andExpect(status().isOk());

        seenResult(userB, room, earlier.getId()).andExpect(status().isOk());

        assertThat(reload(memberB).getSeenResultRoundDate()).isEqualTo(NOV_4);
    }

    @Test
    void ROOM_06_3_다른_방의_회차면_거부하고_저장하지_않는다() throws Exception {
        Round round = pickedA();
        Room otherRoom = newRoom();
        addMember(otherRoom, userB, "지수", BosalColor.PURPLE);

        seenResult(userB, otherRoom, round.getId())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON_002"));

        assertThat(roomMemberRepository.findByRoom_IdAndUser_IdAndLeftAtIsNull(otherRoom.getId(), userB.getId())
                .orElseThrow().getSeenResultRoundDate()).isNull();
    }

    // ---------- 준비 ----------

    /** B·C·D가 A를, A가 B를 골라 A가 지목된 11/4 회차. */
    private Round pickedA() {
        return announced(NOV_4, picks(memberB, memberA, memberC, memberA, memberD, memberA, memberA, memberB));
    }

    /** 표: (투표자, 대상) 순서쌍. 대상이 null이면 "오늘은 모두 보살". */
    private static Map<RoomMember, RoomMember> picks(RoomMember... pairs) {
        Map<RoomMember, RoomMember> votes = new java.util.LinkedHashMap<>();
        for (int i = 0; i < pairs.length; i += 2) {
            votes.put(pairs[i], pairs[i + 1]);
        }
        return votes;
    }

    /** 그 날짜 회차를 인증 → 다음 날 03:00 투표 시작 → 표 → 다음 날 15:00 마감까지 진행한다. A는 못 참았어요(23,000원)로 인증한다. */
    private Round announced(LocalDate date, Map<RoomMember, RoomMember> votes) {
        Round round = startVoting(date);
        votes.forEach((voter, target) -> voteRepository.save(Vote.create(reload(round), voter, target)));
        voteCloseService.closeAtDeadline(room.getId(), round.getId(), date.plusDays(1).atTime(15, 0));
        Round closed = reload(round);
        assertThat(closed.getStatus()).isEqualTo(RoundStatus.ANNOUNCED);
        return closed;
    }

    private Round startVoting(LocalDate date) {
        Round round = roundRepository.save(Round.create(room, date));
        for (RoomMember member : new RoomMember[] {memberA, memberB, memberC, memberD}) {
            boolean isA = member.getId().equals(memberA.getId());
            certificationRepository.save(Certification.create(round, member,
                    isA ? CertificationResult.FAILED : CertificationResult.ENDURED, "기록",
                    isA ? 23000 : null, date.atTime(20, 0)));
        }
        voteStartService.startAtDeadline(room.getId(), round.getId(), date.plusDays(1).atTime(3, 0));
        return reload(round);
    }

    private void defend(Round round) {
        Round loaded = reload(round);
        ReflectionTestUtils.setField(loaded, "resultType", RoundResultType.DEFENDED);
        roundRepository.save(loaded);
    }

    private ResultActions result(User user, Round round) throws Exception {
        return mockMvc.perform(get("/api/rounds/{roundId}/result", round.getId()).header("Authorization", bearer(user)));
    }

    private ResultActions tap(User user, Round round, int count) throws Exception {
        return mockMvc.perform(post("/api/rounds/{roundId}/taps", round.getId())
                .header("Authorization", bearer(user))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"count\": " + count + "}"));
    }

    private ResultActions received(User user, Round round) throws Exception {
        return mockMvc.perform(get("/api/rounds/{roundId}/taps/received", round.getId())
                .header("Authorization", bearer(user)));
    }

    private ResultActions played(User user, Round round) throws Exception {
        return mockMvc.perform(put("/api/rounds/{roundId}/taps/received/played", round.getId())
                .header("Authorization", bearer(user)));
    }

    private ResultActions seenResult(User user, Room target, Long roundId) throws Exception {
        return mockMvc.perform(put("/api/rooms/{roomId}/members/me/seen-result", target.getId())
                .header("Authorization", bearer(user))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"roundId\": " + roundId + "}"));
    }

    private ResultActions voteTab(User user) throws Exception {
        return mockMvc.perform(get("/api/rooms/{roomId}/vote", room.getId()).header("Authorization", bearer(user)));
    }

    private Tap tapOf(Round round, RoomMember member) {
        return tapRepository.findAll().stream()
                .filter(tap -> tap.getRound().getId().equals(round.getId())
                        && tap.getFromMember().getId().equals(member.getId()))
                .findFirst().orElseThrow();
    }

    private String bearer(User user) {
        return "Bearer " + jwtProvider.createAccessToken(user.getId());
    }

    private Round reload(Round round) {
        return roundRepository.findById(round.getId()).orElseThrow();
    }

    private RoomMember reload(RoomMember member) {
        return roomMemberRepository.findById(member.getId()).orElseThrow();
    }

    private Room newRoom() {
        return roomRepository.save(Room.create("배달 끊기 방", RoomGoal.DELIVERY, 4));
    }

    private User newUser() {
        return userRepository.save(User.create("anon-result-" + userSequence++));
    }

    private RoomMember addMember(Room target, User user, String nickname, BosalColor color) {
        return addMember(target, user, nickname, color, NOV_2.minusDays(10).atStartOfDay());
    }

    private RoomMember addMember(Room target, User user, String nickname, BosalColor color, LocalDateTime joinedAt) {
        return roomMemberRepository.save(RoomMember.create(target, user, nickname, color, joinedAt));
    }
}
