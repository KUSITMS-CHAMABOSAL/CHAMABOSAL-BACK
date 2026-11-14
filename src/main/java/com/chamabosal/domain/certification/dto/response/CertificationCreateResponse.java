package com.chamabosal.domain.certification.dto.response;

import com.chamabosal.domain.certification.entity.Certification;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** 한 솥 적립 결과(potEarned)는 POT-01에서 추가한다. */
public record CertificationCreateResponse(
        Long certificationId,
        @Schema(description = "회차 날짜") LocalDate roundDate,
        LocalDateTime certifiedAt,
        @Schema(description = "수정·삭제할 수 있는 마지막 시각 (등록 10분 뒤)") LocalDateTime editableUntil,
        @Schema(description = "이 인증으로 받은 개인 공양미. 방 인원이 1명이면 0") int riceEarned,
        @Schema(description = "이 등록으로 전원 인증이 되어 투표가 시작됐는지") boolean isVoteStarted
) {

    public static CertificationCreateResponse from(Certification certification, int riceEarned, boolean isVoteStarted) {
        return new CertificationCreateResponse(certification.getId(), certification.getRound().getRoundDate(),
                certification.getCertifiedAt(), certification.getEditableUntil(), riceEarned, isVoteStarted);
    }
}
