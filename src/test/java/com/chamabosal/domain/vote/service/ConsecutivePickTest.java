package com.chamabosal.domain.vote.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.chamabosal.domain.certification.entity.Certification;
import com.chamabosal.domain.certification.entity.CertificationResult;
import com.chamabosal.domain.certification.repository.CertificationRepository;
import com.chamabosal.domain.room.entity.BosalColor;
import com.chamabosal.domain.room.entity.Room;
import com.chamabosal.domain.room.entity.RoomGoal;
import com.chamabosal.domain.room.entity.RoomMember;
import com.chamabosal.domain.room.repository.RoomMemberRepository;
import com.chamabosal.domain.room.repository.RoomRepository;
import com.chamabosal.domain.round.entity.Round;
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
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

/**
 * 연속 지목 (RESULT-04 5번, Q-23). 휴식이 없어 연속으로 지목돼도 다음 회차 후보다.
 * 회차는 실제 흐름(전원 인증 → 다음 날 03:00 투표 시작 → 표 → 다음 날 15:00 마감)으로 하루씩 진행한다.
 */
@SpringBootTest
@Import(MutableClock.Config.class)
class ConsecutivePickTest {

    private static final LocalDate DAY_1 = LocalDate.of(2026, 11, 1);

    @Autowired MutableClock clock;
    @Autowired VoteStartService voteStartService;
    @Autowired VoteCloseService voteCloseService;
    @Autowired UserRepository userRepository;
    @Autowired RoomRepository roomRepository;
    @Autowired RoomMemberRepository roomMemberRepository;
    @Autowired RoundRepository roundRepository;
    @Autowired RoundParticipantRepository roundParticipantRepository;
    @Autowired CertificationRepository certificationRepository;
    @Autowired VoteRepository voteRepository;

    private Room room;
    private RoomMember memberA;
    private RoomMember memberB;

    @BeforeEach
    void setUp() {
        clock.set(DAY_1.atTime(10, 0));
        room = roomRepository.save(Room.create("배달 끊기 방", RoomGoal.DELIVERY, 4, 300));
        memberA = addMember("민지");
        memberB = addMember("지수");
        addMember("하린");
        addMember("도윤");
    }

    @AfterEach
    void tearDown() {
        voteRepository.deleteAll();
        roundParticipantRepository.deleteAll();
        certificationRepository.deleteAll();
        roundRepository.deleteAll();
        roomMemberRepository.deleteAll();
        roomRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    void RESULT_04_1_3회_연속_지목돼도_다음_회차_후보다() {
        pickA(DAY_1);
        pickA(DAY_1.plusDays(1));
        pickA(DAY_1.plusDays(2));

        Round next = startVoting(DAY_1.plusDays(3));
        assertThat(roundParticipantRepository.findByRound_IdAndMember_Id(next.getId(), memberA.getId())
                .orElseThrow().isCandidate()).isTrue();
    }

    /** A를 뺀 전원이 A를, A는 B를 골라 A가 지목된다. */
    private void pickA(LocalDate date) {
        Round round = startVoting(date);
        for (RoomMember voter : roomMemberRepository.findByRoom_IdAndLeftAtIsNull(room.getId())) {
            voteRepository.save(Vote.create(round, voter, voter.getId().equals(memberA.getId()) ? memberB : memberA));
        }
        voteCloseService.closeAtDeadline(room.getId(), round.getId(), date.plusDays(1).atTime(15, 0));
        Round closed = roundRepository.findById(round.getId()).orElseThrow();
        assertThat(closed.getStatus()).isEqualTo(RoundStatus.ANNOUNCED);
        assertThat(closed.getResultType()).isEqualTo(RoundResultType.PICKED);
    }

    private Round startVoting(LocalDate date) {
        Round round = roundRepository.save(Round.create(room, date));
        List<RoomMember> members = roomMemberRepository.findByRoom_IdAndLeftAtIsNull(room.getId());
        for (RoomMember member : members) {
            certificationRepository.save(Certification.create(
                    round, member, CertificationResult.ENDURED, "기록", null, date.atTime(20, 0)));
        }
        voteStartService.startAtDeadline(room.getId(), round.getId(), date.plusDays(1).atTime(3, 0));
        return roundRepository.findById(round.getId()).orElseThrow();
    }

    private RoomMember addMember(String nickname) {
        User user = userRepository.save(User.create("anon-pick-" + nickname));
        return roomMemberRepository.save(
                RoomMember.create(room, user, nickname, BosalColor.PINK, DAY_1.minusDays(10).atStartOfDay()));
    }
}
