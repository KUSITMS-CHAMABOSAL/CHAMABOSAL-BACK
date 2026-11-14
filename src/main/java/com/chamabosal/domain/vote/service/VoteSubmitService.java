package com.chamabosal.domain.vote.service;

import com.chamabosal.domain.room.entity.RoomMember;
import com.chamabosal.domain.room.repository.RoomMemberRepository;
import com.chamabosal.domain.room.repository.RoomRepository;
import com.chamabosal.domain.round.entity.Round;
import com.chamabosal.domain.round.entity.RoundParticipant;
import com.chamabosal.domain.round.repository.RoundParticipantRepository;
import com.chamabosal.domain.round.repository.RoundRepository;
import com.chamabosal.domain.vote.dto.request.VoteSubmitRequest;
import com.chamabosal.domain.vote.entity.VoteTargetType;
import com.chamabosal.domain.vote.dto.response.VoteSubmitResponse;
import com.chamabosal.domain.vote.entity.Vote;
import com.chamabosal.domain.vote.repository.VoteRepository;
import com.chamabosal.global.common.exception.BaseErrorCode;
import com.chamabosal.global.common.exception.BaseException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;

/**
 * 투표 제출 (VOTE-03). 방 행 락 안에서 명단·투표권·중복·대상을 확인하고 저장한 뒤,
 * 같은 트랜잭션에서 마감 확인(VOTE-04)까지 이어서 전원 투표 시 바로 마감한다.
 */
@Service
@RequiredArgsConstructor
public class VoteSubmitService {

    private final RoomRepository roomRepository;
    private final RoomMemberRepository roomMemberRepository;
    private final RoundRepository roundRepository;
    private final RoundParticipantRepository roundParticipantRepository;
    private final VoteRepository voteRepository;
    private final VoteCloseService voteCloseService;
    private final Clock clock;

    /**
     * MySQL 기본 격리 수준(REPEATABLE READ)은 트랜잭션의 첫 일반 조회 시점 스냅샷을 계속 읽어서,
     * 락을 잡은 뒤에 읽어도 그 전의 일반 조회(roomId 조회)가 이미 스냅샷을 고정해 버린다.
     * 락을 잡은 뒤의 조회가 그 시점의 커밋된 값을 보도록 READ COMMITTED로 읽는다 (InviteService.join과 같은 이유).
     */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public VoteSubmitResponse submit(Long userId, Long roundId, VoteSubmitRequest request) {
        validateRequest(request);

        // roomId만 먼저 구하고, 방 행 락을 잡은 뒤에야 회차와 현재 시각을 읽는다.
        Long roomId = roundRepository.findRoomIdByRoundId(roundId)
                .orElseThrow(() -> new BaseException(BaseErrorCode.ROUND_NOT_FOUND));
        roomRepository.findWithLockById(roomId)
                .filter(found -> !found.isDeleted())
                .orElseThrow(() -> new BaseException(BaseErrorCode.ROOM_NOT_FOUND));
        LocalDateTime now = LocalDateTime.now(clock);
        RoomMember me = roomMemberRepository.findByRoom_IdAndUser_IdAndLeftAtIsNull(roomId, userId)
                .orElseThrow(() -> new BaseException(BaseErrorCode.NOT_ROOM_MEMBER));
        Round round = roundRepository.findById(roundId)
                .orElseThrow(() -> new BaseException(BaseErrorCode.ROUND_NOT_FOUND));
        // 15:00 마감 배치가 늦게 돌아 아직 VOTING으로 남아 있을 수 있어, 상태뿐 아니라 마감 시각도 같이 본다.
        if (!round.isVoting() || !now.isBefore(round.getVoteClosesAt())) {
            throw new BaseException(BaseErrorCode.VOTE_NOT_OPEN);
        }

        RoundParticipant myParticipant = roundParticipantRepository.findByRound_IdAndMember_Id(roundId, me.getId())
                .filter(participant -> !me.getJoinedAt().isAfter(round.getVoteStartedAt()))
                .orElseThrow(() -> new BaseException(BaseErrorCode.NOT_IN_VOTE_LIST));
        if (!myParticipant.isCanVote()) {
            throw new BaseException(BaseErrorCode.NO_VOTING_RIGHT);
        }
        if (voteRepository.existsByRound_IdAndVoterMember_Id(roundId, me.getId())) {
            throw new BaseException(BaseErrorCode.ALREADY_VOTED);
        }

        RoomMember target = resolveTarget(request, roundId, me.getId());
        voteRepository.save(Vote.create(round, me, target));

        boolean isClosed = voteCloseService.closeIfAllVoted(round, now);
        return new VoteSubmitResponse(isClosed);
    }

    private void validateRequest(VoteSubmitRequest request) {
        boolean memberTargetMissing = request.targetType() == VoteTargetType.MEMBER && request.targetMemberId() == null;
        boolean allBosalHasTarget = request.targetType() == VoteTargetType.ALL_BOSAL && request.targetMemberId() != null;
        if (memberTargetMissing || allBosalHasTarget) {
            throw BaseException.withField(BaseErrorCode.INVALID_REQUEST,
                    "targetType이 MEMBER면 targetMemberId가 필요하고, ALL_BOSAL이면 보낼 수 없습니다.", "targetMemberId");
        }
    }

    /** ALL_BOSAL이면 대상이 없다. MEMBER면 본인이 아니고, 명단의 선택 가능한 후보여야 한다. */
    private RoomMember resolveTarget(VoteSubmitRequest request, Long roundId, Long voterMemberId) {
        if (request.targetType() == VoteTargetType.ALL_BOSAL) {
            return null;
        }
        if (request.targetMemberId().equals(voterMemberId)) {
            throw new BaseException(BaseErrorCode.SELF_VOTE_NOT_ALLOWED);
        }
        RoundParticipant targetParticipant = roundParticipantRepository
                .findByRound_IdAndMember_Id(roundId, request.targetMemberId())
                .orElseThrow(() -> new BaseException(BaseErrorCode.INVALID_VOTE_TARGET));
        if (!targetParticipant.isCandidate() || targetParticipant.isResting()) {
            throw new BaseException(BaseErrorCode.INVALID_VOTE_TARGET);
        }
        return targetParticipant.getMember();
    }
}
