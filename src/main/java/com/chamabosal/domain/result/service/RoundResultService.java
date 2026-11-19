package com.chamabosal.domain.result.service;

import com.chamabosal.domain.certification.repository.CertificationRepository;
import com.chamabosal.domain.defense.repository.DefenseTicketRepository;
import com.chamabosal.domain.defense.service.DefenseService;
import com.chamabosal.domain.result.dto.response.RoundResultResponse;
import com.chamabosal.domain.result.dto.response.RoundResultResponse.Picked;
import com.chamabosal.domain.result.dto.response.RoundResultResponse.VoteCount;
import com.chamabosal.domain.room.entity.RoomMember;
import com.chamabosal.domain.room.repository.RoomMemberRepository;
import com.chamabosal.domain.round.entity.Round;
import com.chamabosal.domain.round.entity.RoundResultType;
import com.chamabosal.domain.round.entity.RoundStatus;
import com.chamabosal.domain.round.repository.RoundRepository;
import com.chamabosal.domain.store.service.StoreService;
import com.chamabosal.domain.tap.repository.TapRepository;
import com.chamabosal.domain.vote.entity.Vote;
import com.chamabosal.domain.vote.repository.VoteRepository;
import com.chamabosal.global.common.exception.BaseErrorCode;
import com.chamabosal.global.common.exception.BaseException;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 결과 화면 (RESULT-02). 지목된 사람이 처음 열면 그 시각을 남겨서 읽기 전용이 아니다.
 * 지목된 본인에게는 보유한 미사용 방어권 개수와 지금 쓰면 성공하는지를 담는다 (SHIELD-02와 같은 기한 판단).
 */
@Service
@RequiredArgsConstructor
public class RoundResultService {

    private final RoundRepository roundRepository;
    private final RoomMemberRepository roomMemberRepository;
    private final VoteRepository voteRepository;
    private final CertificationRepository certificationRepository;
    private final TapRepository tapRepository;
    private final DefenseTicketRepository defenseTicketRepository;
    private final DefenseService defenseService;
    private final StoreService storeService;
    private final Clock clock;

    @Transactional(readOnly = true)
    public RoundResultResponse getResult(Long userId, Long roundId) {
        Round round = roundRepository.findById(roundId)
                .orElseThrow(() -> new BaseException(BaseErrorCode.ROUND_NOT_FOUND));
        Long roomId = round.getRoom().getId();
        RoomMember me = roomMemberRepository.findByRoom_IdAndUser_IdAndLeftAtIsNull(roomId, userId)
                .orElseThrow(() -> new BaseException(BaseErrorCode.NOT_ROOM_MEMBER));
        if (round.getStatus() != RoundStatus.ANNOUNCED) {
            throw new BaseException(BaseErrorCode.RESULT_NOT_ANNOUNCED);
        }

        RoundResultType resultType = round.getResultType();
        RoomMember picked = round.getPickedMember();
        boolean isMePicked = picked != null && picked.getId().equals(me.getId());
        boolean isPicked = resultType == RoundResultType.PICKED;
        LocalDateTime now = LocalDateTime.now(clock);

        boolean isTapExpired = isPicked && !round.isTapOpen(now);
        // 지목된 사람이 방을 나갔으면 톡톡이 없다 (RESULT-02 4번, Q-27).
        List<Vote> votes = voteRepository.findWithTargetByRound_Id(roundId);
        // 투표한 사람만 톡톡한다. 투표 시작 뒤 다시 들어왔으면 표가 남아 있어도 명단 밖이다 (RESULT-02 4번, TapService와 같은 기준).
        boolean hasVoted = !round.isJoinedAfterVoteStart(me)
                && votes.stream().anyMatch(vote -> vote.getVoterMember().getId().equals(me.getId()));
        boolean canTap = isPicked && !isMePicked && !isTapExpired && picked.isCurrent() && hasVoted
                && !tapRepository.existsByRound_IdAndFromMember_Id(roundId, me.getId());
        // 득표 수는 지목된 보살이 없을 때만 보여준다 (RESULT-02 2번).
        boolean isCountShown = picked == null;

        Integer ticketCount = isMePicked && (isPicked || resultType == RoundResultType.DEFENDED)
                ? (int) defenseTicketRepository.countByUser_IdAndUsedAtIsNull(userId) : null;

        return new RoundResultResponse(
                round.getId(), round.getRoundDate(), resultType,
                picked == null ? null : Picked.from(picked, certificationRepository
                        .findByRound_IdAndMember_IdAndDeletedAtIsNull(roundId, picked.getId()).orElse(null),
                        storeService.getEquippedItemCodesByMember(List.of(picked)).get(picked.getId())),
                isCountShown ? voteCounts(votes) : List.of(),
                isCountShown ? (int) votes.stream().filter(vote -> vote.getTargetMember() == null).count() : null,
                isMePicked,
                isMePicked && isPicked ? round.getDefenseClosesAt() : null,
                ticketCount,
                isMePicked && isPicked && ticketCount > 0 && !defenseService.isExpired(round, now)
                        && defenseService.hasCertified(round) && !defenseService.isMoktakDone(userId, roundId),
                canTap,
                isTapExpired,
                (int) tapRepository.countByRound_IdAndCountGreaterThanAndFromMember_IdNot(roundId, 0, me.getId()));
    }

    /** 표를 받은 멤버만, 득표 수가 많은 순. 같으면 멤버 ID 순이다. */
    private List<VoteCount> voteCounts(List<Vote> votes) {
        Map<RoomMember, Long> counts = votes.stream()
                .filter(vote -> vote.getTargetMember() != null)
                .collect(Collectors.groupingBy(Vote::getTargetMember, Collectors.counting()));
        return counts.entrySet().stream()
                .sorted(Comparator.<Map.Entry<RoomMember, Long>>comparingLong(Map.Entry::getValue).reversed()
                        .thenComparing(entry -> entry.getKey().getId()))
                .map(entry -> VoteCount.from(entry.getKey(), entry.getValue()))
                .toList();
    }
}
