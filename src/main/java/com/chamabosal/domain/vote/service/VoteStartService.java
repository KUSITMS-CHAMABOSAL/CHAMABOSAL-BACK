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
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 투표 시작 (VOTE-01, CERT-05). 마지막 인증, 03:00 배치, 나가기 세 경로가 모두 방 행 락 안에서 이 클래스를 부른다.
 * 락 안에서 회차 상태를 다시 읽고 인증 중일 때만 시작해서, 경로가 겹쳐도 한 번만 시작한다.
 */
@Service
@RequiredArgsConstructor
public class VoteStartService {

    private static final int MIN_VOTERS = 3;

    private final RoomRepository roomRepository;
    private final RoomMemberRepository roomMemberRepository;
    private final RoundRepository roundRepository;
    private final RoundParticipantRepository roundParticipantRepository;
    private final CertificationRepository certificationRepository;

    /** 방 행 락을 잡은 트랜잭션 안에서 부른다. 현재 멤버 전원이 인증했으면 시작하고, 투표가 열렸으면 true. */
    @Transactional(propagation = Propagation.MANDATORY)
    public boolean startIfAllCertified(Round round, LocalDateTime now) {
        if (!round.isCertifying()) {
            return false;
        }
        List<RoomMember> members = roomMemberRepository.findByRoom_IdAndLeftAtIsNull(round.getRoom().getId());
        Set<Long> certified = new HashSet<>(certificationRepository.findCertifiedMemberIds(round.getId()));
        if (!members.stream().allMatch(member -> certified.contains(member.getId()))) {
            return false;
        }
        return start(round, members, certified, now);
    }

    /** 나가기(ROOM-07)에서 방 행 락을 잡은 채 부른다. 방마다 인증 중인 회차는 많아야 하나다. */
    @Transactional(propagation = Propagation.MANDATORY)
    public boolean startIfAllCertified(Long roomId, LocalDateTime now) {
        return roundRepository.findFirstByRoom_IdAndStatusOrderByRoundDateDesc(roomId, RoundStatus.CERTIFYING)
                .map(round -> startIfAllCertified(round, now))
                .orElse(false);
    }

    /** 03:00 배치 대상. 지금 회차보다 이른 날짜인데 아직 인증 중인 회차. 트랜잭션 밖에서 쓰도록 ID만 돌려준다. */
    @Transactional(readOnly = true)
    public List<DueRound> findDueRounds(LocalDateTime now) {
        return roundRepository.findByStatusAndRoundDateBefore(RoundStatus.CERTIFYING, Round.dateOf(now)).stream()
                .map(round -> new DueRound(round.getRoom().getId(), round.getId()))
                .toList();
    }

    public record DueRound(Long roomId, Long roundId) {
    }

    /**
     * 03:00 배치에서 회차마다 따로 부른다. 방 행 락을 먼저 잡고 회차를 읽어야 락을 기다리는 동안 바뀐 상태를 본다.
     * 삭제된 방의 회차는 그대로 둔다 (ROOM-07 11번).
     */
    @Transactional
    public void startAtDeadline(Long roomId, Long roundId, LocalDateTime now) {
        Room room = roomRepository.findWithLockById(roomId).orElse(null);
        if (room == null || room.isDeleted()) {
            return;
        }
        Round round = roundRepository.findById(roundId).orElseThrow();
        if (!round.isCertifying()) {
            return;
        }
        List<RoomMember> members = roomMemberRepository.findByRoom_IdAndLeftAtIsNull(roomId);
        Set<Long> certified = new HashSet<>(certificationRepository.findCertifiedMemberIds(roundId));
        start(round, members, certified, now);
    }

    /** 명단 3명 미만이거나 인증한 멤버가 0명이면 투표 없음 (VOTE-01 4번). */
    private boolean start(Round round, List<RoomMember> members, Set<Long> certified, LocalDateTime now) {
        boolean anyCertified = members.stream().anyMatch(member -> certified.contains(member.getId()));
        if (members.size() < MIN_VOTERS || !anyCertified) {
            round.markNoVote(now);
            return false;
        }
        LocalDate roundDate = round.getRoundDate();
        List<RoundParticipant> participants = members.stream()
                .map(member -> snapshot(round, member, certified.contains(member.getId()), roundDate))
                .toList();
        roundParticipantRepository.saveAll(participants);
        round.startVote(now);
        return true;
    }

    /** 휴식 중이거나 그날 들어와서 인증하지 않은 멤버는 후보가 아니다. 인증하지 않은 기존 멤버는 `인증 안 함` 후보다 (CERT-05). */
    private RoundParticipant snapshot(Round round, RoomMember member, boolean hasCertified, LocalDate roundDate) {
        boolean isResting = roundDate.equals(member.getRestingRoundDate());
        boolean joinedThisRound = Round.dateOf(member.getJoinedAt()).equals(roundDate);
        boolean isCandidate = !isResting && (hasCertified || !joinedThisRound);
        return RoundParticipant.create(round, member, isCandidate, hasCertified, isResting);
    }
}
