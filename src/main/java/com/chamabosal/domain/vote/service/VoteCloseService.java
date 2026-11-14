package com.chamabosal.domain.vote.service;

import com.chamabosal.domain.room.entity.Room;
import com.chamabosal.domain.room.entity.RoomMember;
import com.chamabosal.domain.room.repository.RoomRepository;
import com.chamabosal.domain.round.entity.Round;
import com.chamabosal.domain.round.entity.RoundParticipant;
import com.chamabosal.domain.round.entity.RoundResultType;
import com.chamabosal.domain.round.entity.RoundStatus;
import com.chamabosal.domain.round.repository.RoundParticipantRepository;
import com.chamabosal.domain.round.repository.RoundRepository;
import com.chamabosal.domain.vote.entity.Vote;
import com.chamabosal.domain.vote.repository.VoteRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 투표 마감과 결과 판정 (VOTE-04, RESULT-01). 투표 제출(VOTE-03, 다음 PR), 15:00 배치, 나가기 세 경로가 모두
 * 방 행 락 안에서 이 클래스를 부른다. 락 안에서 회차 상태를 다시 읽고 투표 중일 때만 마감해서, 경로가 겹쳐도 한 번만 마감한다.
 */
@Service
@RequiredArgsConstructor
public class VoteCloseService {

    private final RoomRepository roomRepository;
    private final RoundRepository roundRepository;
    private final RoundParticipantRepository roundParticipantRepository;
    private final VoteRepository voteRepository;

    /** 방 행 락을 잡은 트랜잭션 안에서 부른다. 필수 투표자 전원이 표를 냈으면 마감하고, 마감했으면 true. */
    @Transactional(propagation = Propagation.MANDATORY)
    public boolean closeIfAllVoted(Round round, LocalDateTime now) {
        if (!round.isVoting()) {
            return false;
        }
        List<Vote> votes = voteRepository.findByRound_Id(round.getId());
        if (!allRequiredVoted(round, votes)) {
            return false;
        }
        close(round, votes, now);
        return true;
    }

    /**
     * 스스로 방 행 락을 잡아서, 이미 락을 잡은 트랜잭션(나가기)에서 불러도 되고 단독으로 불러도 안전하다
     * (이미 잡은 락을 다시 잡는 것은 같은 트랜잭션 안에서 아무 효과가 없다). 방마다 투표 중인 회차는 많아야 하나다.
     */
    @Transactional
    public boolean closeIfAllVoted(Long roomId, LocalDateTime now) {
        roomRepository.findWithLockById(roomId);
        return roundRepository.findFirstByRoom_IdAndStatusOrderByRoundDateDesc(roomId, RoundStatus.VOTING)
                .map(round -> closeIfAllVoted(round, now))
                .orElse(false);
    }

    /** 15:00 배치 대상. 투표 중인 회차가 많지 않아 상태로만 가져와 자바에서 마감 시각을 거른다. */
    @Transactional(readOnly = true)
    public List<DueRound> findDueRounds(LocalDateTime now) {
        return roundRepository.findByStatus(RoundStatus.VOTING).stream()
                .filter(round -> !now.isBefore(round.getVoteClosesAt()))
                .map(round -> new DueRound(round.getRoom().getId(), round.getId()))
                .toList();
    }

    public record DueRound(Long roomId, Long roundId) {
    }

    /** 15:00 배치에서 회차마다 따로 부른다. 필수 투표자가 다 안 찼어도 마감 시각이 지났으면 그대로 마감한다. */
    @Transactional
    public void closeAtDeadline(Long roomId, Long roundId, LocalDateTime now) {
        Room room = roomRepository.findWithLockById(roomId).orElse(null);
        if (room == null || room.isDeleted()) {
            return;
        }
        Round round = roundRepository.findById(roundId).orElseThrow();
        if (!round.isVoting()) {
            return;
        }
        close(round, voteRepository.findByRound_Id(round.getId()), now);
    }

    private void close(Round round, List<Vote> votes, LocalDateTime now) {
        Judgement judgement = judge(votes);
        round.announce(now, judgement.resultType(), judgement.pickedMember());
    }

    /**
     * 투표권 있는 명단 멤버 중, 나간 멤버와 투표 시작 뒤 나갔다가 다시 들어온 멤버는 기다리지 않는다 (VOTE-04 1·5·6번, Q-36).
     * 그 나머지(필수 투표자) 전원이 표를 냈으면 전원 투표다. 필수 투표자가 0명이면 그 자체로 전원 투표다.
     */
    private boolean allRequiredVoted(Round round, List<Vote> votes) {
        Set<Long> votedMemberIds = votes.stream().map(vote -> vote.getVoterMember().getId())
                .collect(Collectors.toSet());
        return roundParticipantRepository.findCanVoteWithMemberByRound_Id(round.getId()).stream()
                .map(RoundParticipant::getMember)
                .filter(member -> isRequired(member, round))
                .allMatch(member -> votedMemberIds.contains(member.getId()));
    }

    private boolean isRequired(RoomMember member, Round round) {
        return member.isCurrent() && !member.getJoinedAt().isAfter(round.getVoteStartedAt());
    }

    /**
     * RESULT-01: 실제 투표자의 절반을 초과해 "오늘은 모두 보살"이면 ALL_BOSAL이다.
     * 아니면 "오늘은 모두 보살"이 아닌 표만 모아 최다 득표가 단독이면 PICKED, 비었거나 동점이면 ALL_SAFE다
     * (0표·동점·전원 보살 아님이 모두 이 분기 하나로 처리된다). 방어권(SHIELD-02)으로 DEFENDED가 되는 것은 제외 범위다.
     */
    private Judgement judge(List<Vote> votes) {
        int totalVoters = votes.size();
        long allBosalCount = votes.stream().filter(vote -> vote.getTargetMember() == null).count();
        if (totalVoters > 0 && allBosalCount * 2 > totalVoters) {
            return new Judgement(RoundResultType.ALL_BOSAL, null);
        }

        Map<RoomMember, Long> counts = votes.stream()
                .filter(vote -> vote.getTargetMember() != null)
                .collect(Collectors.groupingBy(Vote::getTargetMember, Collectors.counting()));
        long max = counts.values().stream().max(Comparator.naturalOrder()).orElse(0L);
        List<RoomMember> winners = counts.entrySet().stream()
                .filter(entry -> entry.getValue() == max)
                .map(Map.Entry::getKey)
                .toList();
        if (max == 0 || winners.size() != 1) {
            return new Judgement(RoundResultType.ALL_SAFE, null);
        }
        return new Judgement(RoundResultType.PICKED, winners.get(0));
    }

    private record Judgement(RoundResultType resultType, RoomMember pickedMember) {
    }
}
