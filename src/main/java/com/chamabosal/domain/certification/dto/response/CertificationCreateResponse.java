package com.chamabosal.domain.certification.dto.response;

import com.chamabosal.domain.certification.entity.Certification;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** 공양미 적립 결과(riceEarned, potEarned)는 RICE-01·POT-01에서 추가한다. */
public record CertificationCreateResponse(
        Long certificationId,
        @Schema(description = "회차 날짜") LocalDate roundDate,
        LocalDateTime certifiedAt,
        @Schema(description = "수정·삭제할 수 있는 마지막 시각 (등록 10분 뒤)") LocalDateTime editableUntil,
        @Schema(description = "이 등록으로 전원 인증이 되어 투표가 시작됐는지") boolean isVoteStarted
) {

    public static CertificationCreateResponse from(Certification certification, boolean isVoteStarted) {
        return new CertificationCreateResponse(certification.getId(), certification.getRound().getRoundDate(),
                certification.getCertifiedAt(), certification.getEditableUntil(), isVoteStarted);
    }
}
