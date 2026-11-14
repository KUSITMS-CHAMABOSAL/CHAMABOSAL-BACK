package com.chamabosal.domain.certification.dto.response;

import com.chamabosal.domain.certification.entity.Certification;
import com.chamabosal.domain.certification.entity.CertificationResult;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;

/** 사진(imageUrl)은 CERT-04에서 추가한다. */
public record CertificationUpdateResponse(
        Long certificationId,
        CertificationResult result,
        String memo,
        Integer spentAmount,
        @Schema(description = "수정해도 바뀌지 않는다 (등록 10분 뒤)") LocalDateTime editableUntil
) {

    public static CertificationUpdateResponse from(Certification certification) {
        return new CertificationUpdateResponse(certification.getId(), certification.getResult(),
                certification.getMemo(), certification.getSpentAmount(), certification.getEditableUntil());
    }
}
