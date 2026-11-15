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
import org.springframework.transaction.annotation.Isolation;
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
    private final Clock clock;

    /**
     * 지목된 사람이 동시에 처음 열면 뒤 요청의 조건부 UPDATE는 0건이 된다. REPEATABLE READ면 다시 읽어도 트랜잭션 첫 조회의
     * 스냅샷(비어 있는 처음 조회 시각)을 보므로, 먼저 커밋된 시각을 읽도록 READ COMMITTED로 읽는다 (코드리뷰 반영).
     */
    @Transactional(isolation = Isolation.READ_COMMITTED)
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
        LocalDateTime pickedSeenAt = round.getPickedSeenAt();
        if (isMePicked && isPicked && pickedSeenAt == null) {
            LocalDateTime now = LocalDateTime.now(clock);
            roundRepository.markPickedSeen(roundId, now);
            // 동시에 연 다른 요청이 먼저 남겼으면 그 값을 쓴다. 조건부 UPDATE가 영속성 컨텍스트를 비워 DB에서 다시 읽는다.
            round = roundRepository.findById(roundId).orElseThrow();
            picked = round.getPickedMember();
            pickedSeenAt = round.getPickedSeenAt();
            // 그사이 방어가 커밋됐으면 DEFENDED로 바뀌어 있다 (코드리뷰 반영).
            resultType = round.getResultType();
            isPicked = resultType == RoundResultType.PICKED;
        }

        boolean isTapExpired = isPicked && roundRepository.existsByRoom_IdAndRoundDateAfterAndStatusIn(
                roomId, round.getRoundDate(), List.of(RoundStatus.VOTING, RoundStatus.ANNOUNCED));
        boolean canTap = isPicked && !isMePicked && !isTapExpired
                && !tapRepository.existsByRound_IdAndFromMember_Id(roundId, me.getId());

        Integer ticketCount = isMePicked && (isPicked || resultType == RoundResultType.DEFENDED)
                ? (int) defenseTicketRepository.countByUser_IdAndUsedAtIsNull(userId) : null;

        List<Vote> votes = voteRepository.findWithTargetByRound_Id(roundId);
        return new RoundResultResponse(
                round.getId(), round.getRoundDate(), resultType,
                picked == null ? null : Picked.from(picked, certificationRepository
                        .findByRound_IdAndMember_IdAndDeletedAtIsNull(roundId, picked.getId()).orElse(null)),
                voteCounts(votes),
                (int) votes.stream().filter(vote -> vote.getTargetMember() == null).count(),
                isMePicked,
                isMePicked && isPicked ? pickedSeenAt.plusMinutes(DefenseService.DEFENSE_MINUTES) : null,
                ticketCount,
                isMePicked && isPicked && ticketCount > 0 && !defenseService.isExpired(round, LocalDateTime.now(clock)),
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
