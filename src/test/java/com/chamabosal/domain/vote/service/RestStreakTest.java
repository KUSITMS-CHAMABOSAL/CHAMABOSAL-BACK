package com.chamabosal.domain.vote.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.chamabosal.domain.certification.entity.Certification;
import com.chamabosal.domain.certification.entity.CertificationResult;
import com.chamabosal.domain.certification.repository.CertificationRepository;
import com.chamabosal.domain.rice.repository.RiceHistoryRepository;
import com.chamabosal.domain.room.dto.request.InviteJoinRequest;
import com.chamabosal.domain.room.entity.BosalColor;
import com.chamabosal.domain.room.entity.Room;
import com.chamabosal.domain.room.entity.RoomGoal;
import com.chamabosal.domain.room.entity.RoomMember;
import com.chamabosal.domain.room.repository.RoomMemberRepository;
import com.chamabosal.domain.room.repository.RoomRepository;
import com.chamabosal.domain.room.service.InviteService;
import com.chamabosal.domain.room.service.RoomService;
import com.chamabosal.domain.round.entity.Round;
import com.chamabosal.domain.round.entity.RoundParticipant;
import com.chamabosal.domain.round.entity.RoundResultType;
import com.chamabosal.domain.round.entity.RoundStatus;
import com.chamabosal.domain.round.repository.RoundParticipantRepository;
import com.chamabosal.domain.round.repository.RoundRepository;
import com.chamabosal.domain.user.entity.User;
import com.chamabosal.domain.user.repository.UserRepository;
import com.chamabosal.domain.vote.entity.Vote;
import com.chamabosal.domain.vote.repository.VoteRepository;
import com.chamabosal.support.MutableClock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * 연속 지목 휴식 (RESULT-04). 공통 Given: 방 인원은 A, B, C, D 4명이다.
 * 회차는 실제 흐름(전원 인증 → 다음 날 03:00 투표 시작 → 표 → 다음 날 15:00 마감)으로 하루씩 진행한다.
 */
@SpringBootTest
@Import(MutableClock.Config.class)
class RestStreakTest {

    private static final LocalDate DAY_1 = LocalDate.of(2026, 11, 1);

    @Autowired
    MutableClock clock;

    @Autowired
    VoteStartService voteStartService;

    @Autowired
    VoteCloseService voteCloseService;

    @Autowired
    RoomService roomService;

    @Autowired
    InviteService inviteService;

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
    RiceHistoryRepository riceHistoryRepository;

    private int userSequence;
    private Room room;
    private User userA;
    private User userB;
    private RoomMember memberA;
    private RoomMember memberB;
    private RoomMember memberC;
    private RoomMember memberD;

    @BeforeEach
    void setUp() {
        clock.set(DAY_1.atTime(10, 0));
        room = newRoom();
        userA = newUser();
        userB = newUser();
        memberA = addMember(room, userA, "민지");
        memberB = addMember(room, userB, "지수");
        memberC = addMember(room, newUser(), "하린");
        memberD = addMember(room, newUser(), "도윤");
    }

    @AfterEach
    void tearDown() {
        riceHistoryRepository.deleteAll();
        voteRepository.deleteAll();
        roundParticipantRepository.deleteAll();
        certificationRepository.deleteAll();
        roundRepository.deleteAll();
        roomMemberRepository.deleteAll();
        roomRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    void RESULT_04_1_3회_연속_지목이면_다음_회차에_휴식한다() {
        pick(room, day(1), memberA);
        pick(room, day(2), memberA);
        pick(room, day(3), memberA);

        assertThat(reload(memberA).getPickedStreak()).isEqualTo(3);
        assertThat(reload(memberA).getRestingRoundDate()).isEqualTo(day(4));
        RoundParticipant next = participant(startVoting(room, day(4)), memberA);
        assertThat(next.isResting()).isTrue();
        assertThat(next.isCandidate()).isFalse();
    }

    @Test
    void RESULT_04_2_방어도_연속에_포함되고_이번_달_지목은_2번이다() {
        pick(room, day(1), memberA);
        defend(pick(room, day(2), memberA));
        pick(room, day(3), memberA);

        assertThat(reload(memberA).getRestingRoundDate()).isEqualTo(day(4));
        long monthlyPicked = roundRepository.findAll().stream()
                .filter(round -> round.getResultType() == RoundResultType.PICKED)
                .filter(round -> round.getPickedMember().getId().equals(memberA.getId()))
                .count();
        assertThat(monthlyPicked).isEqualTo(2);
    }

    @Test
    void RESULT_04_3_다른_사람이_지목되면_0이다() {
        pick(room, day(1), memberA);
        pick(room, day(2), memberA);

        pick(room, day(3), memberB);

        assertThat(reload(memberA).getPickedStreak()).isZero();
        assertThat(reload(memberB).getPickedStreak()).isEqualTo(1);
    }

    @Test
    void RESULT_04_4_모두_보살이나_모두_무사면_0이다() {
        pick(room, day(1), memberA);
        pick(room, day(2), memberA);
        allBosal(room, day(3));
        assertThat(reload(memberA).getPickedStreak()).isZero();

        pick(room, day(4), memberA);
        pick(room, day(5), memberA);
        allSafe(room, day(6));
        assertThat(reload(memberA).getPickedStreak()).isZero();
    }

    @Test
    void RESULT_04_5_휴식이_끝나면_다음_회차에는_다시_후보다() {
        pick(room, day(1), memberA);
        pick(room, day(2), memberA);
        pick(room, day(3), memberA);

        pick(room, day(4), memberB);

        assertThat(reload(memberA).getPickedStreak()).isZero();
        assertThat(reload(memberA).getRestingRoundDate()).isNull();
        RoundParticipant next = participant(startVoting(room, day(5)), memberA);
        assertThat(next.isResting()).isFalse();
        assertThat(next.isCandidate()).isTrue();
    }

    /** 휴식 날짜(4일)에 아무도 인증하지 않아 회차가 없어도 휴식을 쓴 것으로 보고, 다음 투표가 시작되면 0이다 (RESULT-04 4·5번, 코드리뷰 반영). */
    @Test
    void 휴식_날짜에_투표가_없어도_다음_투표가_시작되면_연속_지목은_0이다() {
        pick(room, day(1), memberA);
        pick(room, day(2), memberA);
        pick(room, day(3), memberA);

        RoundParticipant next = participant(startVoting(room, day(5)), memberA);

        assertThat(reload(memberA).getPickedStreak()).isZero();
        assertThat(reload(memberA).getRestingRoundDate()).isNull();
        assertThat(next.isResting()).isFalse();
        assertThat(next.isCandidate()).isTrue();
    }

    @Test
    void RESULT_04_6_다른_방에서는_휴식이_아니다() {
        Room room2 = newRoom();
        RoomMember memberA2 = addMember(room2, userA, "민지");
        addMember(room2, newUser(), "은우");
        addMember(room2, newUser(), "서연");
        pick(room, day(1), memberA);
        pick(room, day(2), memberA);
        pick(room, day(3), memberA);

        RoundParticipant inRoom2 = participant(startVoting(room2, day(4)), memberA2);

        assertThat(inRoom2.isResting()).isFalse();
        assertThat(reload(memberA2).getPickedStreak()).isZero();
    }

    @Test
    void RESULT_04_7_6개월_안에_다시_들어오면_연속_지목이_이어진다() {
        pick(room, day(1), memberA);
        pick(room, day(2), memberA);
        clock.set(day(3).atTime(10, 0));
        roomService.leave(userA.getId(), room.getId());

        clock.set(day(10).atTime(10, 0));
        RoomMember rejoined = rejoinByInvite(userA);

        assertThat(rejoined.getId()).isEqualTo(memberA.getId());
        assertThat(rejoined.getPickedStreak()).isEqualTo(2);
    }

    @Test
    void RESULT_04_8_6개월이_지나_다시_들어오면_새로_시작한다() {
        pick(room, day(1), memberA);
        pick(room, day(2), memberA);
        clock.set(day(3).atTime(10, 0));
        roomService.leave(userA.getId(), room.getId());

        clock.set(day(3).plusMonths(7).atTime(10, 0));
        RoomMember rejoined = rejoinByInvite(userA);

        assertThat(rejoined.getId()).isNotEqualTo(memberA.getId());
        assertThat(rejoined.getPickedStreak()).isZero();
    }

    @Test
    void RESULT_04_9_다른_사람이_방어한_결과도_다른_사람_지목이다() {
        pick(room, day(1), memberA);
        pick(room, day(2), memberA);

        defend(pick(room, day(3), memberB));

        assertThat(reload(memberA).getPickedStreak()).isZero();
        assertThat(reload(memberB).getPickedStreak()).isEqualTo(1);
    }

    @Test
    void RESULT_04_10_나가_있는_동안의_결과는_반영하지_않는다() {
        pick(room, day(1), memberA);
        pick(room, day(2), memberA);
        clock.set(day(3).atTime(10, 0));
        roomService.leave(userA.getId(), room.getId());

        pick(room, day(3), memberB);
        assertThat(reload(memberA).getPickedStreak()).isEqualTo(2);

        clock.set(day(10).atTime(10, 0));
        assertThat(rejoinByInvite(userA).getPickedStreak()).isEqualTo(2);
    }

    // ---------- 준비 ----------

    private static LocalDate day(int n) {
        return DAY_1.plusDays(n - 1);
    }

    /** 지목될 사람을 뺀 현재 멤버 전원이 그 사람을, 그 사람은 다른 한 명을 골라 지목된다. */
    private Round pick(Room target, LocalDate date, RoomMember picked) {
        List<RoomMember> members = currentMembers(target);
        RoomMember other = members.stream().filter(m -> !m.getId().equals(picked.getId())).findFirst().orElseThrow();
        Round round = announce(target, date, members, voter -> voter.getId().equals(picked.getId()) ? other : picked);
        assertThat(round.getResultType()).isEqualTo(RoundResultType.PICKED);
        return round;
    }

    private void allBosal(Room target, LocalDate date) {
        Round round = announce(target, date, currentMembers(target), voter -> null);
        assertThat(round.getResultType()).isEqualTo(RoundResultType.ALL_BOSAL);
    }

    /** A·B가 2표씩 받아 동점이라 모두 무사다. */
    private void allSafe(Room target, LocalDate date) {
        Round round = announce(target, date, currentMembers(target),
                voter -> voter.getId().equals(memberA.getId()) || voter.getId().equals(memberC.getId()) ? memberB : memberA);
        assertThat(round.getResultType()).isEqualTo(RoundResultType.ALL_SAFE);
    }

    private Round announce(Room target, LocalDate date, List<RoomMember> voters,
                           java.util.function.Function<RoomMember, RoomMember> choice) {
        Round round = startVoting(target, date);
        for (RoomMember voter : voters) {
            voteRepository.save(Vote.create(reload(round), voter, choice.apply(voter)));
        }
        voteCloseService.closeAtDeadline(target.getId(), round.getId(), date.plusDays(1).atTime(15, 0));
        Round closed = reload(round);
        assertThat(closed.getStatus()).isEqualTo(RoundStatus.ANNOUNCED);
        return closed;
    }

    private Round startVoting(Room target, LocalDate date) {
        Round round = roundRepository.save(Round.create(target, date));
        for (RoomMember member : currentMembers(target)) {
            certificationRepository.save(Certification.create(
                    round, member, CertificationResult.ENDURED, "기록", null, date.atTime(20, 0)));
        }
        voteStartService.startAtDeadline(target.getId(), round.getId(), date.plusDays(1).atTime(3, 0));
        return reload(round);
    }

    private void defend(Round round) {
        ReflectionTestUtils.setField(round, "resultType", RoundResultType.DEFENDED);
        roundRepository.save(round);
    }

    private RoomMember rejoinByInvite(User user) {
        String token = inviteService.issueToken(userB.getId(), room.getId()).response().inviteToken();
        inviteService.join(user.getId(), token, new InviteJoinRequest("민지"));
        return roomMemberRepository.findByRoom_IdAndUser_IdAndLeftAtIsNull(room.getId(), user.getId()).orElseThrow();
    }

    private RoundParticipant participant(Round round, RoomMember member) {
        return roundParticipantRepository.findByRound_IdAndMember_Id(round.getId(), member.getId()).orElseThrow();
    }

    private List<RoomMember> currentMembers(Room target) {
        return roomMemberRepository.findByRoom_IdAndLeftAtIsNull(target.getId());
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
        return userRepository.save(User.create("anon-rest-" + userSequence++));
    }

    private RoomMember addMember(Room target, User user, String nickname) {
        return roomMemberRepository.save(
                RoomMember.create(target, user, nickname, BosalColor.PINK, DAY_1.minusDays(10).atStartOfDay()));
    }
}
