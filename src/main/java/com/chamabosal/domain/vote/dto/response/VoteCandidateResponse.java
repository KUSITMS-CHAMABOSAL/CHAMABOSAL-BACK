package com.chamabosal.domain.vote.dto.response;

import com.chamabosal.domain.certification.entity.Certification;
import com.chamabosal.domain.certification.entity.CertificationResult;
import com.chamabosal.domain.room.entity.BosalColor;
import com.chamabosal.domain.room.entity.RoomMember;
import com.chamabosal.domain.round.entity.RoundParticipant;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import java.util.List;

/** 투표 후보 한 명 (VOTE-02). */
public record VoteCandidateResponse(
        Long memberId,
        String nickname,
        BosalColor color,
        @Schema(description = "착용 아이템 코드, 상점 노출 순서") List<String> equippedItemCodes,
        CertificationSummary certification
) {

    public static VoteCandidateResponse from(RoundParticipant participant, Certification certification, String imageUrl,
                                             List<String> equippedItemCodes) {
        RoomMember member = participant.getMember();
        return new VoteCandidateResponse(
                member.getId(), member.getNickname(), member.getColor(), equippedItemCodes,
                CertificationSummary.from(certification, imageUrl));
    }

    public record CertificationSummary(
            CertificationResult result, String memo, Integer spentAmount, String imageUrl, LocalDateTime certifiedAt
    ) {

        public static CertificationSummary from(Certification certification, String imageUrl) {
            if (certification == null) {
                return null;
            }
            return new CertificationSummary(certification.getResult(), certification.getMemo(),
                    certification.getSpentAmount(), imageUrl, certification.getCertifiedAt());
        }
    }
}
