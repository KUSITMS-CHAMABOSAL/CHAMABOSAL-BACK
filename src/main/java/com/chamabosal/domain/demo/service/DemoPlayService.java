package com.chamabosal.domain.demo.service;

import com.chamabosal.domain.nabal.entity.NabalLoss;
import com.chamabosal.domain.nabal.repository.NabalLossRepository;
import com.chamabosal.domain.nabal.service.NabalPicker;
import com.chamabosal.domain.room.entity.RoomMember;
import com.chamabosal.domain.room.repository.RoomMemberRepository;
import com.chamabosal.domain.round.entity.Round;
import com.chamabosal.domain.round.entity.RoundParticipant;
import com.chamabosal.domain.round.repository.RoundParticipantRepository;
import com.chamabosal.domain.user.repository.UserRepository;
import com.chamabosal.domain.vote.entity.Vote;
import com.chamabosal.domain.vote.repository.VoteRepository;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 시연 방에서만 시간 조건을 건너뛰는 자동 처리 (DEMO-02, DEMO-03). 시연 설정이 꺼져 있거나 시연 방이 아니면 아무것도 하지 않아
 * 일반 방의 동작은 그대로다. 투표 시작·톡톡 보내기가 방 행 락을 잡은 트랜잭션 안에서 부른다.
 */
@Service
@RequiredArgsConstructor
public class DemoPlayService {

    private final DemoSettings demoSettings;
    private final RoomMemberRepository roomMemberRepository;
    private final RoundParticipantRepository roundParticipantRepository;
    private final VoteRepository voteRepository;
    private final NabalLossRepository nabalLossRepository;
    private final NabalPicker nabalPicker;
    private final UserRepository userRepository;

    /**
     * DEMO-02: 투표가 시작되면 가짜 팀원 표를 넣는다. A → C, B → C, C → 모두 보살이라 방문객이 누구를 골라도 C가 2표로 단독 최다다.
     * 투표권이 있는 가짜 팀원만 넣는다. 투표 보상은 주지 않는다 (5번). 시연 계정에게 투표권이 없으면(03:00 배치로 시작) 넣지 않는다.
     * 가짜 팀원 표만으로 전원 투표가 되면 제출 때만 도는 조기 마감이 없어 15:00까지 투표 중으로 남기 때문이다 (6번).
     * 일반 방은 방금 저장한 명단으로 시연 계정이 있는지만 보고 바로 끝내서, 멤버·사용자를 더 읽지 않는다 (코드리뷰 반영).
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void onVoteStarted(Round round) {
        if (!demoSettings.isEnabled()) {
            return;
        }
        List<RoomMember> voters = roundParticipantRepository.findByRound_Id(round.getId()).stream()
                .filter(RoundParticipant::isCanVote)
                .map(RoundParticipant::getMember)
                .toList();
        if (voters.stream().noneMatch(member -> demoSettings.isDemoAccount(member.getUser().getId()))) {
            return;
        }
        Map<DemoFakeMember, RoomMember> fakes = findFakeMembers(round.getRoom().getId());
        RoomMember target = fakes.get(DemoFakeMember.C);
        if (target == null) {
            return;
        }
        Set<Long> voterIds = voters.stream().map(RoomMember::getId).collect(Collectors.toSet());
        fakes.forEach((fake, member) -> {
            if (voterIds.contains(member.getId())) {
                voteRepository.save(Vote.create(round, member, fake == DemoFakeMember.C ? null : target));
            }
        });
    }

    /**
     * DEMO-03: 시연 방에서 시연 계정이 톡톡을 보내면(건너뛰기 포함) 나발을 1개 뺀다. 번호는 NABAL-01처럼 남은 나발 중 랜덤이고,
     * 같은 방·회차 날짜는 한 번만 뺀다. 나발 심기(NABAL-03)·나발 빠짐 판정(NABAL-01)처럼 사용자 행 락을 잡고 빠진 나발을 읽어
     * 같은 번호를 두 번 고르지 않는다 (코드리뷰 반영). 나발 심기는 사용자 행만 잠가 방 → 사용자 순서여도 엇갈리지 않는다.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void onTapSent(Round round, RoomMember sender) {
        Long userId = sender.getUser().getId();
        Long roomId = round.getRoom().getId();
        if (!demoSettings.isEnabled() || !demoSettings.isDemoAccount(userId) || !isDemoRoom(roomId)
                || nabalLossRepository.existsByUser_IdAndRoom_IdAndRoundDate(userId, roomId, round.getRoundDate())) {
            return;
        }
        userRepository.findWithLockById(userId);
        Set<Integer> fallen = nabalLossRepository.findByUser_IdAndReplantedAtIsNullOrderByNabalNumber(userId).stream()
                .map(NabalLoss::getNabalNumber)
                .collect(Collectors.toSet());
        OptionalInt picked = nabalPicker.pick(fallen);
        if (picked.isPresent()) {
            nabalLossRepository.save(NabalLoss.create(sender.getUser(), round.getRoom(), round.getRoundDate(),
                    picked.getAsInt()));
        }
    }

    private boolean isDemoRoom(Long roomId) {
        return roomMemberRepository.existsByRoom_IdAndLeftAtIsNullAndUser_TossAnonKeyStartingWith(
                roomId, DemoSettings.FAKE_KEY_PREFIX);
    }

    private Map<DemoFakeMember, RoomMember> findFakeMembers(Long roomId) {
        Map<DemoFakeMember, RoomMember> fakes = new EnumMap<>(DemoFakeMember.class);
        for (RoomMember member : roomMemberRepository.findByRoom_IdAndLeftAtIsNull(roomId)) {
            DemoFakeMember.fromTossAnonKey(member.getUser().getTossAnonKey())
                    .ifPresent(fake -> fakes.put(fake, member));
        }
        return fakes;
    }
}
