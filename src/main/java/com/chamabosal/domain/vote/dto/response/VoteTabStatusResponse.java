package com.chamabosal.domain.vote.dto.response;

import com.chamabosal.domain.room.entity.BosalColor;
import com.chamabosal.domain.room.entity.RoomMember;
import com.chamabosal.domain.vote.entity.VoteTabStatus;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/** 투표 탭 상태 (VOTE-05). 상태마다 정해진 필드만 채우고 나머지는 비운다 (api.md). */
public record VoteTabStatusResponse(
        VoteTabStatus status,
        Integer memberCount,
        Integer uncertifiedCount,
        MemberSummary firstUncertifiedMember,
        Long roundId,
        LocalDate roundDate,
        LocalDateTime closesAt,
        Integer votedCount,
        Integer voterCount,
        List<VoteCandidateResponse> candidates,
        LocalDateTime tapClosesAt,
        Long moktakCount
) {

    public static VoteTabStatusResponse underThree(int memberCount) {
        return new VoteTabStatusResponse(VoteTabStatus.UNDER_THREE, memberCount,
                null, null, null, null, null, null, null, null, null, null);
    }

    public static VoteTabStatusResponse beforeFirstVote(int uncertifiedCount, MemberSummary firstUncertifiedMember) {
        return new VoteTabStatusResponse(VoteTabStatus.BEFORE_FIRST_VOTE, null,
                uncertifiedCount, firstUncertifiedMember, null, null, null, null, null, null, null, null);
    }

    public static VoteTabStatusResponse notInList(Long roundId, LocalDate roundDate, LocalDateTime closesAt) {
        return new VoteTabStatusResponse(VoteTabStatus.NOT_IN_LIST, null, null, null,
                roundId, roundDate, closesAt, null, null, null, null, null);
    }

    public static VoteTabStatusResponse noVotingRight(Long roundId, LocalDate roundDate, LocalDateTime closesAt,
                                                       List<VoteCandidateResponse> candidates) {
        return new VoteTabStatusResponse(VoteTabStatus.NO_VOTING_RIGHT, null, null, null,
                roundId, roundDate, closesAt, null, null, candidates, null, null);
    }

    public static VoteTabStatusResponse voting(Long roundId, LocalDate roundDate, LocalDateTime closesAt,
                                                int votedCount, int voterCount, List<VoteCandidateResponse> candidates) {
        return new VoteTabStatusResponse(VoteTabStatus.VOTING, null, null, null,
                roundId, roundDate, closesAt, votedCount, voterCount, candidates, null, null);
    }

    public static VoteTabStatusResponse voted(Long roundId, LocalDate roundDate, LocalDateTime closesAt,
                                               int votedCount, int voterCount) {
        return new VoteTabStatusResponse(VoteTabStatus.VOTED, null, null, null,
                roundId, roundDate, closesAt, votedCount, voterCount, null, null, null);
    }

    public static VoteTabStatusResponse resultUnseen(Long roundId, LocalDate roundDate) {
        return new VoteTabStatusResponse(VoteTabStatus.RESULT_UNSEEN, null, null, null,
                roundId, roundDate, null, null, null, null, null, null);
    }

    public static VoteTabStatusResponse resultSeen(Long roundId, LocalDate roundDate) {
        return new VoteTabStatusResponse(VoteTabStatus.RESULT_SEEN, null, null, null,
                roundId, roundDate, null, null, null, null, null, null);
    }

    public static VoteTabStatusResponse tap(Long roundId, LocalDate roundDate, LocalDateTime tapClosesAt) {
        return new VoteTabStatusResponse(VoteTabStatus.TAP, null, null, null,
                roundId, roundDate, null, null, null, null, tapClosesAt, null);
    }

    public static VoteTabStatusResponse moktak(Long roundId, LocalDate roundDate, long moktakCount) {
        return new VoteTabStatusResponse(VoteTabStatus.MOKTAK, null, null, null,
                roundId, roundDate, null, null, null, null, null, moktakCount);
    }

    public static VoteTabStatusResponse noVote(Long roundId, LocalDate roundDate) {
        return new VoteTabStatusResponse(VoteTabStatus.NO_VOTE, null, null, null,
                roundId, roundDate, null, null, null, null, null, null);
    }

    public record MemberSummary(Long memberId, String nickname, BosalColor color) {

        public static MemberSummary from(RoomMember member) {
            return new MemberSummary(member.getId(), member.getNickname(), member.getColor());
        }
    }
}
