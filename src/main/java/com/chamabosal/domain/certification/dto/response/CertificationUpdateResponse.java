package com.chamabosal.domain.certification.dto.response;

import com.chamabosal.domain.certification.entity.Certification;
import com.chamabosal.domain.certification.entity.CertificationResult;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;

public record CertificationUpdateResponse(
        Long certificationId,
        CertificationResult result,
        String memo,
        Integer spentAmount,
        @Schema(description = "사진 조회 URL (30분 유효). 사진이 없으면 null") String imageUrl,
        @Schema(description = "수정해도 바뀌지 않는다 (등록 10분 뒤)") LocalDateTime editableUntil
) {

    public static CertificationUpdateResponse from(Certification certification, String imageUrl) {
        return new CertificationUpdateResponse(certification.getId(), certification.getResult(),
                certification.getMemo(), certification.getSpentAmount(), imageUrl, certification.getEditableUntil());
    }
}
