package com.chamabosal.domain.room.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.chamabosal.domain.auth.service.AuthUserService;
import com.chamabosal.domain.auth.service.AuthUserService.AuthUser;
import com.chamabosal.domain.certification.entity.Certification;
import com.chamabosal.domain.certification.entity.CertificationResult;
import com.chamabosal.domain.defense.entity.DefenseTicket;
import com.chamabosal.domain.defense.repository.DefenseTicketRepository;
import com.chamabosal.domain.nabal.entity.NabalLoss;
import com.chamabosal.domain.nabal.repository.NabalLossRepository;
import com.chamabosal.domain.rice.entity.RiceHistory;
import com.chamabosal.domain.rice.entity.RiceReason;
import com.chamabosal.domain.rice.entity.RiceRefType;
import com.chamabosal.domain.rice.service.RiceService;
import com.chamabosal.domain.room.dto.request.RoomCreateRequest;
import com.chamabosal.domain.room.entity.KickBlock;
import com.chamabosal.domain.room.entity.LeaveType;
import com.chamabosal.domain.room.entity.Room;
import com.chamabosal.domain.room.entity.RoomGoal;
import com.chamabosal.domain.room.entity.RoomMember;
import com.chamabosal.domain.room.repository.KickBlockRepository;
import com.chamabosal.domain.round.entity.Round;
import com.chamabosal.domain.round.entity.RoundResultType;
import com.chamabosal.domain.round.entity.RoundStatus;
import com.chamabosal.domain.user.entity.PushAgreement;
import com.chamabosal.domain.user.entity.PushAgreementType;
import com.chamabosal.domain.user.entity.User;
import com.chamabosal.domain.user.repository.PushAgreementRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

/** USER-05 내 데이터 삭제. 방 준비 코드를 쓰려고 방 테스트 패키지에 둔다. */
class UserDataDeleteTest extends RoomTestSupport {

    private static final String ANON_KEY_A = "anon-key-a";

    @Autowired
    AuthUserService authUserService;

    @Autowired
    RiceService riceService;

    @Autowired
    DefenseTicketRepository defenseTicketRepository;

    @Autowired
    PushAgreementRepository pushAgreementRepository;

    @Autowired
    KickBlockRepository kickBlockRepository;

    @Autowired
    NabalLossRepository nabalLossRepository;

    private User userA;

    @BeforeEach
    void setUp() {
        userA = userRepository.save(User.create(ANON_KEY_A));
    }

    @AfterEach
    void cleanUp() {
        kickBlockRepository.deleteAll();
        nabalLossRepository.deleteAll();
        defenseTicketRepository.deleteAll();
        pushAgreementRepository.deleteAll();
    }

    @Test
    void USER_05_1_참여_중인_모든_방에서_DATA_DELETED로_나가고_삭제_시각이_저장된다() throws Exception {
        RoomMember member1 = addMember(createRoom(4), userA, "민지");
        RoomMember member2 = addMember(createRoom(4), userA, "민지");

        deleteData(userA).andExpect(status().isOk());

        for (RoomMember member : new RoomMember[]{member1, member2}) {
            RoomMember left = roomMemberRepository.findById(member.getId()).orElseThrow();
            assertThat(left.getLeaveType()).isEqualTo(LeaveType.DATA_DELETED);
            assertThat(left.getLeftAt()).isEqualTo(NOW);
            assertThat(left.getUser()).isNull();
        }
        assertThat(userRepository.findById(userA.getId()).orElseThrow().getDeletedAt()).isEqualTo(NOW);
    }

    @Test
    void USER_05_2_방장이_삭제하면_다음으로_먼저_들어온_멤버가_방장이_된다() throws Exception {
        Room room = ownRoom(userA);
        RoomMember memberC = addMember(room, newUser(), "하린");
        RoomMember memberB = roomMemberRepository.save(
                RoomMember.create(room, newUser(), "지수", unusedColor(room), NOW.minusDays(2)));

        deleteData(userA).andExpect(status().isOk());

        assertThat(roomRepository.findById(room.getId()).orElseThrow().getOwnerMemberId())
                .isEqualTo(memberB.getId())
                .isNotEqualTo(memberC.getId());
    }

    @Test
    void USER_05_3_혼자인_방은_삭제된다() throws Exception {
        Room room = ownRoom(userA);

        deleteData(userA).andExpect(status().isOk());

        assertThat(roomRepository.findById(room.getId()).orElseThrow().isDeleted()).isTrue();
    }

    @Test
    void USER_05_4_삭제한_뒤에는_만료_전_토큰도_COMMON_008이다() throws Exception {
        String token = bearer(userA);

        mockMvc.perform(delete("/api/users/me").header("Authorization", token)).andExpect(status().isOk());

        mockMvc.perform(get("/api/users/me").header("Authorization", token))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("COMMON_008"));
    }

    @Test
    void USER_05_5_다시_로그인하면_새_사용자이고_공양미와_방어권과_방이_없다() throws Exception {
        addMember(createRoom(4), userA, "민지");
        riceHistoryRepository.save(RiceHistory.create(
                userA, null, 120, RiceReason.CERTIFICATION, RiceRefType.CERTIFICATION, 1L));
        defenseTicketRepository.save(DefenseTicket.purchase(userA, NOW.minusDays(1)));

        deleteData(userA).andExpect(status().isOk());
        AuthUser again = authUserService.findOrCreate(ANON_KEY_A);

        assertThat(again.isNew()).isTrue();
        assertThat(again.id()).isNotEqualTo(userA.getId());
        assertThat(riceService.getDisplayBalance(again.id())).isZero();
        assertThat(defenseTicketRepository.countByUser_IdAndUsedAtIsNull(again.id())).isZero();
        User newUser = userRepository.findById(again.id()).orElseThrow();
        mockMvc.perform(get("/api/users/me").header("Authorization", bearer(newUser)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.rooms.length()").value(0));
    }

    @Test
    void USER_05_7_남은_멤버에게는_떠난_보살로_보이고_한_줄과_금액은_남는다() throws Exception {
        Room room = ownRoom(userA);
        User userB = newUser();
        addMember(room, userB, "지수");
        RoomMember memberA = roomMemberRepository.findByRoom_IdAndUser_IdAndLeftAtIsNull(room.getId(), userA.getId())
                .orElseThrow();
        Round round = saveRound(room, NOW.toLocalDate().minusDays(1), RoundStatus.VOTING);
        Certification certification = certificationRepository.save(Certification.create(
                round, memberA, CertificationResult.FAILED, "치킨 시켰어요", 23000, NOW.minusDays(1)));
        round.announce(NOW.minusHours(1), RoundResultType.PICKED, memberA);
        roundRepository.save(round);

        deleteData(userA).andExpect(status().isOk());

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
    void USER_05_8_공양미_기록과_방어권과_알림_동의를_바로_지우고_식별키를_비운다() throws Exception {
        Room room = ownRoom(userA);
        addMember(room, newUser(), "지수");
        riceHistoryRepository.save(RiceHistory.create(
                userA, room, 10, RiceReason.CERTIFICATION, RiceRefType.CERTIFICATION, 1L));
        defenseTicketRepository.save(DefenseTicket.purchase(userA, NOW.minusDays(1)));
        pushAgreementRepository.save(PushAgreement.create(userA, PushAgreementType.CERT_REMINDER, NOW.minusDays(1)));
        nabalLossRepository.save(NabalLoss.create(userA, room, NOW.toLocalDate().minusDays(1), 7));
        User other = newUser();
        riceHistoryRepository.save(RiceHistory.create(
                other, room, 10, RiceReason.CERTIFICATION, RiceRefType.CERTIFICATION, 2L));

        deleteData(userA).andExpect(status().isOk());

        assertThat(riceHistoryRepository.findAll())
                .extracting(history -> history.getUser().getId())
                .containsExactly(other.getId());
        assertThat(defenseTicketRepository.count()).isZero();
        assertThat(pushAgreementRepository.count()).isZero();
        assertThat(nabalLossRepository.count()).isZero();
        assertThat(userRepository.findById(userA.getId()).orElseThrow().getTossAnonKey()).isNull();
    }

    @Test
    void USER_05_9_내보내진_뒤_삭제하고_다시_가입해도_그_방에는_들어올_수_없다() throws Exception {
        Room room = createRoomWithInviter(4, newUser());
        RoomMember kicked = addMember(room, userA, "민지");
        leave(kicked.getId(), NOW.minusDays(1), LeaveType.KICKED);
        Room otherRoom = createRoom(4);
        RoomMember otherInviter = addMember(otherRoom, newUser(), "서연");
        otherInviter.issueInviteToken("token-other");
        roomMemberRepository.save(otherInviter);

        deleteData(userA).andExpect(status().isOk());
        User again = userRepository.findById(authUserService.findOrCreate(ANON_KEY_A).id()).orElseThrow();

        mockMvc.perform(get("/api/invites/{token}", TOKEN_A).header("Authorization", bearer(again)))
                .andExpect(jsonPath("$.data.status").value("UNAVAILABLE"));
        join(again, TOKEN_A)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ROOM_006"));
        join(again, "token-other").andExpect(status().isCreated());
    }

    @Test
    void 내보내진_적이_없으면_차단_기록을_남기지_않는다() throws Exception {
        addMember(createRoom(4), userA, "민지");

        deleteData(userA).andExpect(status().isOk());

        assertThat(kickBlockRepository.count()).isZero();
    }

    @Test
    void 차단_기록이_만료되면_다시_가입해_들어올_수_있다() throws Exception {
        Room room = createRoomWithInviter(4, newUser());
        RoomMember kicked = addMember(room, userA, "민지");
        leave(kicked.getId(), NOW.minusDays(1), LeaveType.KICKED);
        deleteData(userA).andExpect(status().isOk());

        clock.set(NOW.plusMonths(6).plusMinutes(1));
        User again = userRepository.findById(authUserService.findOrCreate(ANON_KEY_A).id()).orElseThrow();

        join(again, TOKEN_A).andExpect(status().isCreated());
    }

    @Test
    void 지워지지_않은_만료_기록이_있으면_기한을_다시_6개월로_늘린다() throws Exception {
        Room room = createRoomWithInviter(4, newUser());
        kickBlockRepository.save(KickBlock.create(ANON_KEY_A, room, NOW.minusMonths(7)));
        RoomMember kicked = addMember(room, userA, "민지");
        leave(kicked.getId(), NOW.minusDays(1), LeaveType.KICKED);

        deleteData(userA).andExpect(status().isOk());

        assertThat(kickBlockRepository.findAll())
                .singleElement()
                .extracting(KickBlock::getExpiresAt)
                .isEqualTo(NOW.plusMonths(6));
    }

    @Test
    void 나가기_차감_기록을_남기지_않는다() throws Exception {
        Room room = ownRoom(userA);
        addMember(room, newUser(), "지수");

        deleteData(userA).andExpect(status().isOk());

        assertThat(riceHistoryRepository.findAll()).noneMatch(history -> history.getReason() == RiceReason.LEAVE);
    }

    private Room ownRoom(User owner) {
        Long roomId = roomService.create(owner.getId(),
                new RoomCreateRequest("배달 끊기 방", RoomGoal.DELIVERY, 4, 300, "민지")).roomId();
        return roomRepository.findById(roomId).orElseThrow();
    }

    private ResultActions deleteData(User user) throws Exception {
        return mockMvc.perform(delete("/api/users/me").header("Authorization", bearer(user)));
    }

    private ResultActions join(User user, String token) throws Exception {
        return mockMvc.perform(post("/api/invites/{token}/join", token)
                .header("Authorization", bearer(user))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"nickname\":\"도윤\"}"));
    }
}
