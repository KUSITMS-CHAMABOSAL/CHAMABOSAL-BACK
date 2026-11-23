package com.chamabosal.domain.vote.service;

import com.chamabosal.domain.certification.repository.CertificationRepository;
import com.chamabosal.domain.room.entity.Room;
import com.chamabosal.domain.room.entity.RoomMember;
import com.chamabosal.domain.room.repository.RoomMemberRepository;
import com.chamabosal.domain.room.repository.RoomRepository;
import com.chamabosal.domain.round.entity.Round;
import com.chamabosal.domain.round.entity.RoundParticipant;
import com.chamabosal.domain.round.entity.RoundResultType;
import com.chamabosal.domain.round.entity.RoundStatus;
import com.chamabosal.domain.round.repository.RoundParticipantRepository;
import com.chamabosal.domain.round.repository.RoundRepository;
import com.chamabosal.domain.tap.service.MoktakService;
import com.chamabosal.domain.tap.service.TapService;
import com.chamabosal.domain.vote.dto.response.VoteTabStatusResponse.MemberSummary;
import com.chamabosal.domain.vote.dto.response.VoteTabStatusResponse;
import com.chamabosal.domain.vote.repository.VoteRepository;
import com.chamabosal.global.common.exception.BaseErrorCode;
import com.chamabosal.global.common.exception.BaseException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * 투표 탭 상태 (VOTE-05). 투표 중 → 결과 미확인 → 이마 톡톡 → 목탁 수행 → 3명 미만 → 투표 없음 → 결과 확인 후·첫 투표 전 순서로 정한다
 * (5번). 시작한 투표와 결과 뒤 남은 일은 인원이 3명 미만이 돼도 먼저 보여준다 (Q-34).
 */
@Service
@RequiredArgsConstructor
public class VoteTabStatusService {

    private static final int MIN_VOTERS = 3;

    private final RoomRepository roomRepository;
    private final RoomMemberRepository roomMemberRepository;
    private final RoundRepository roundRepository;
    private final RoundParticipantRepository roundParticipantRepository;
    private final CertificationRepository certificationRepository;
    private final VoteRepository voteRepository;
    private final VoteCandidateService voteCandidateService;
    private final TapService tapService;
    private final MoktakService moktakService;
    private final Clock clock;

    @Transactional(readOnly = true)
    public VoteTabStatusResponse getStatus(Long userId, Long roomId) {
        Room room = roomRepository.findById(roomId)
                .filter(found -> !found.isDeleted())
                .orElseThrow(() -> new BaseException(BaseErrorCode.ROOM_NOT_FOUND));
        RoomMember me = roomMemberRepository.findByRoom_IdAndUser_IdAndLeftAtIsNull(roomId, userId)
                .orElseThrow(() -> new BaseException(BaseErrorCode.NOT_ROOM_MEMBER));
        LocalDateTime now = LocalDateTime.now(clock);

        // 시작한 투표는 나가서 3명 미만이 돼도 유지하므로 인원보다 먼저 본다 (VOTE-05 3번, Q-34).
        Optional<Round> votingRound = roundRepository.findFirstByRoom_IdAndStatusOrderByRoundDateDesc(
                roomId, RoundStatus.VOTING);
        if (votingRound.isPresent()) {
            return votingStatus(votingRound.get(), me);
        }
        // 결과 미확인 → 이마 톡톡 → 목탁 수행은 인원보다 먼저 본다. 결과가 나온 뒤 인원이 줄어도 남은 일을 먼저 보여준다 (VOTE-05 5번).
        Optional<Round> announced = roundRepository.findFirstByRoom_IdAndStatusOrderByRoundDateDesc(
                roomId, RoundStatus.ANNOUNCED);
        if (announced.isPresent()) {
            Optional<VoteTabStatusResponse> pending = pendingAfterResult(announced.get(), me, userId, now);
            if (pending.isPresent()) {
                return pending.get();
            }
        }
        int memberCount = (int) roomMemberRepository.countByRoom_IdAndLeftAtIsNull(roomId);
        if (memberCount < MIN_VOTERS) {
            return VoteTabStatusResponse.underThree(memberCount, room.getMemberLimit());
        }
        LocalDate today = Round.dateOf(now);
        Optional<Round> todayRound = roundRepository.findByRoom_IdAndRoundDate(roomId, today);
        Optional<VoteTabStatusResponse> noVote = noVote(room, me, announced, today);
        if (noVote.isPresent()) {
            return noVote.get();
        }
        if (announced.isPresent()) {
            return VoteTabStatusResponse.resultSeen(announced.get().getId(), announced.get().getRoundDate());
        }
        return beforeFirstVote(roomId, me, todayRound);
    }

    private VoteTabStatusResponse votingStatus(Round round, RoomMember me) {
        LocalDateTime closesAt = round.getVoteClosesAt();
        RoundParticipant participant = roundParticipantRepository
                .findByRound_IdAndMember_Id(round.getId(), me.getId())
                .filter(found -> !me.getJoinedAt().isAfter(round.getVoteStartedAt()))
                .orElse(null);
        if (participant == null) {
            return VoteTabStatusResponse.notInList(round.getId(), round.getRoundDate(), closesAt);
        }
        if (!participant.isCanVote()) {
            // 투표권이 없으면 나는 투표자가 아니라서, 본인 제외 없이 나도 `인증 안 함`으로 그대로 보여준다 (VOTE-05-10).
            return VoteTabStatusResponse.noVotingRight(round.getId(), round.getRoundDate(), closesAt,
                    voteCandidateService.findCandidates(round.getId(), null));
        }

        int votedCount = (int) voteRepository.countByRound_Id(round.getId());
        int voterCount = (int) roundParticipantRepository.countByRound_IdAndCanVoteTrue(round.getId());
        if (voteRepository.existsByRound_IdAndVoterMember_Id(round.getId(), me.getId())) {
            return VoteTabStatusResponse.voted(round.getId(), round.getRoundDate(), closesAt, votedCount, voterCount);
        }
        return VoteTabStatusResponse.voting(round.getId(), round.getRoundDate(), closesAt, votedCount, voterCount,
                voteCandidateService.findCandidates(round.getId(), me.getId()));
    }

    /**
     * 가장 최근 발표 회차에서 남은 일. 안 본 결과 → 지금 보낼 수 있는 톡톡 → 지금 해야 하는 목탁 수행 순서다.
     * 톡톡·목탁은 실제 API와 같은 판단을 쓴다 (TapService, MoktakService). 남은 일이 없으면 비어 있다.
     */
    private Optional<VoteTabStatusResponse> pendingAfterResult(Round round, RoomMember me, Long userId,
                                                                LocalDateTime now) {
        if (me.hasUnseenResult(round.getRoundDate())) {
            return Optional.of(VoteTabStatusResponse.resultUnseen(round.getId(), round.getRoundDate()));
        }
        if (round.getResultType() == RoundResultType.PICKED && tapService.findTapBlock(round, me, false) == null) {
            return Optional.of(VoteTabStatusResponse.tap(round.getId(), round.getRoundDate(), round.getTapClosesAt()));
        }
        if (moktakService.isMoktakDue(userId, round, me, now)) {
            return Optional.of(VoteTabStatusResponse.moktak(round.getId(), round.getRoundDate(),
                    moktakService.getMoktakCount(round.getId())));
        }
        return Optional.empty();
    }

    /**
     * 투표 없음 (VOTE-05 6번). 투표가 열리지 않은 직전 회차를 보여준다. 투표 없음은 03:00 배치에서만 확정돼 오늘 회차는 볼 필요가 없다.
     * 인증도 콕도 없던 날은 회차 행이 없어 회차 ID를 비운다. 직전 회차가 아직 인증 중이면 03:00 배치 전이라 보지 않고,
     * 방을 만들거나 내가 들어온 회차보다 이른 회차, 더 늦은 회차(오늘 일찍 열린 회차)가 이미 발표됐으면 보지 않는다.
     */
    private Optional<VoteTabStatusResponse> noVote(Room room, RoomMember me, Optional<Round> announced,
                                                   LocalDate today) {
        LocalDate yesterday = today.minusDays(1);
        if (yesterday.isBefore(Round.dateOf(room.getCreatedAt())) || yesterday.isBefore(Round.dateOf(me.getJoinedAt()))
                || (announced.isPresent() && announced.get().getRoundDate().isAfter(yesterday))) {
            return Optional.empty();
        }
        Optional<Round> round = roundRepository.findByRoom_IdAndRoundDate(room.getId(), yesterday);
        if (round.isPresent() && round.get().getStatus() != RoundStatus.NO_VOTE) {
            return Optional.empty();
        }
        return Optional.of(VoteTabStatusResponse.noVote(round.map(Round::getId).orElse(null), yesterday));
    }

    /** 첫 번째 미인증 멤버를 고르는 순서는 아직 정하지 않아 (Q-25) 입장 순서로 임시 정렬한다. */
    private VoteTabStatusResponse beforeFirstVote(Long roomId, RoomMember me, Optional<Round> todayRound) {
        Set<Long> certifiedIds = todayRound
                .map(round -> new HashSet<>(certificationRepository.findCertifiedMemberIds(round.getId())))
                .orElseGet(HashSet::new);

        List<RoomMember> uncertified = roomMemberRepository.findByRoom_IdAndLeftAtIsNull(roomId).stream()
                .filter(member -> !member.getId().equals(me.getId()))
                .filter(member -> !certifiedIds.contains(member.getId()))
                .sorted(Comparator.comparing(RoomMember::getJoinedAt).thenComparing(RoomMember::getId))
                .toList();
        MemberSummary first = uncertified.isEmpty() ? null : MemberSummary.from(uncertified.get(0));
        return VoteTabStatusResponse.beforeFirstVote(uncertified.size(), first);
    }
}
