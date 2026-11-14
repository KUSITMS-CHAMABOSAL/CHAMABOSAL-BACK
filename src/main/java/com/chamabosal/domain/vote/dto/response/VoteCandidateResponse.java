package com.chamabosal.domain.vote.dto.response;

import com.chamabosal.domain.certification.entity.Certification;
import com.chamabosal.domain.certification.entity.CertificationResult;
import com.chamabosal.domain.room.entity.BosalColor;
import com.chamabosal.domain.room.entity.RoomMember;
import com.chamabosal.domain.round.entity.RoundParticipant;
import java.time.LocalDateTime;
import java.util.List;

/** 투표 후보 한 명 (VOTE-02). 꾸미기 아이템은 아직 그 도메인이 없어 항상 빈 배열이다. */
public record VoteCandidateResponse(
        Long memberId,
        String nickname,
        BosalColor color,
        List<String> equippedItemCodes,
        CertificationSummary certification,
        boolean isSelectable,
        boolean isResting
) {

    public static VoteCandidateResponse from(RoundParticipant participant, Certification certification) {
        RoomMember member = participant.getMember();
        return new VoteCandidateResponse(
                member.getId(), member.getNickname(), member.getColor(), List.of(),
                CertificationSummary.from(certification),
                participant.isCandidate() && !participant.isResting(),
                participant.isResting());
    }

    public record CertificationSummary(
            CertificationResult result, String memo, Integer spentAmount, String imageUrl, LocalDateTime certifiedAt
    ) {

        public static CertificationSummary from(Certification certification) {
            if (certification == null) {
                return null;
            }
            return new CertificationSummary(certification.getResult(), certification.getMemo(),
                    certification.getSpentAmount(), null, certification.getCertifiedAt());
        }
    }
}
