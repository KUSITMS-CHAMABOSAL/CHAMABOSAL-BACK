package com.chamabosal.domain.vote.service;

import com.chamabosal.domain.certification.repository.CertificationRepository;
import com.chamabosal.domain.room.entity.Room;
import com.chamabosal.domain.room.entity.RoomMember;
import com.chamabosal.domain.room.repository.RoomMemberRepository;
import com.chamabosal.domain.room.repository.RoomRepository;
import com.chamabosal.domain.round.entity.Round;
import com.chamabosal.domain.round.entity.RoundParticipant;
import com.chamabosal.domain.round.entity.RoundStatus;
import com.chamabosal.domain.round.repository.RoundParticipantRepository;
import com.chamabosal.domain.round.repository.RoundRepository;
import com.chamabosal.domain.vote.dto.response.VoteTabStatusResponse;
import com.chamabosal.domain.vote.dto.response.VoteTabStatusResponse.MemberSummary;
import com.chamabosal.domain.vote.repository.VoteRepository;
import com.chamabosal.global.common.exception.BaseErrorCode;
import com.chamabosal.global.common.exception.BaseException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * 투표 탭 상태 (VOTE-05). 방 인원 3명 미만을 가장 먼저 확인하고(투표 중 3명 미만이 되는 경우는 Q-34, 미정),
 * 그다음 투표 중인 회차가 있는지, 없으면 가장 최근 발표 회차가 있는지로 나머지 상태를 가른다.
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
    private final Clock clock;

    @Transactional(readOnly = true)
    public VoteTabStatusResponse getStatus(Long userId, Long roomId) {
        Room room = roomRepository.findById(roomId)
                .filter(found -> !found.isDeleted())
                .orElseThrow(() -> new BaseException(BaseErrorCode.ROOM_NOT_FOUND));
        RoomMember me = roomMemberRepository.findByRoom_IdAndUser_IdAndLeftAtIsNull(roomId, userId)
                .orElseThrow(() -> new BaseException(BaseErrorCode.NOT_ROOM_MEMBER));

        int memberCount = (int) roomMemberRepository.countByRoom_IdAndLeftAtIsNull(roomId);
        if (memberCount < MIN_VOTERS) {
            return VoteTabStatusResponse.underThree(memberCount, room.getMemberLimit());
        }

        Optional<Round> votingRound = roundRepository.findFirstByRoom_IdAndStatusOrderByRoundDateDesc(
                roomId, RoundStatus.VOTING);
        if (votingRound.isPresent()) {
            return votingStatus(votingRound.get(), me);
        }
        Optional<Round> announced = roundRepository.findFirstByRoom_IdAndStatusOrderByRoundDateDesc(
                roomId, RoundStatus.ANNOUNCED);
        if (announced.isPresent()) {
            return resultStatus(announced.get(), me);
        }
        return beforeFirstVote(roomId, me);
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

    /** 결과를 본 뒤 ~ 다음 투표 시작 전까지는 가장 최근 발표 회차로 안 봤는지를 가른다. */
    private VoteTabStatusResponse resultStatus(Round latestAnnounced, RoomMember me) {
        boolean seen = me.getSeenResultRoundDate() != null
                && !me.getSeenResultRoundDate().isBefore(latestAnnounced.getRoundDate());
        return seen
                ? VoteTabStatusResponse.resultSeen(latestAnnounced.getId(), latestAnnounced.getRoundDate())
                : VoteTabStatusResponse.resultUnseen(latestAnnounced.getId(), latestAnnounced.getRoundDate());
    }

    /** 첫 번째 미인증 멤버를 고르는 순서는 아직 정하지 않아 (Q-25) 입장 순서로 임시 정렬한다. */
    private VoteTabStatusResponse beforeFirstVote(Long roomId, RoomMember me) {
        LocalDateTime now = LocalDateTime.now(clock);
        Set<Long> certifiedIds = roundRepository.findByRoom_IdAndRoundDate(roomId, Round.dateOf(now))
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
