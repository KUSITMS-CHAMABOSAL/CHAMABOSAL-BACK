package com.chamabosal.domain.certification.dto.request;

import com.chamabosal.domain.certification.entity.CertificationResult;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

/** 등록과 같은 값을 모두 보내 통째로 바꾼다 (CERT-02 8번). 사진(imageKey, isImageKept)은 CERT-04에서 추가한다. */
public record CertificationUpdateRequest(
        @Schema(description = "참았어요(ENDURED) / 못 참았어요(FAILED)")
        @NotNull(message = "결과를 골라 주세요.")
        CertificationResult result,

        @Schema(description = "한 줄 기록 1~40자. 띄어쓰기만 있으면 안 됨", example = "치킨 반 마리만 시켰어요")
        @NotNull(message = "한 줄 기록을 입력해 주세요.")
        String memo,

        @Schema(description = "쓴 금액. FAILED면 필수 1~999,999, ENDURED면 저장된 금액을 지움", example = "23000")
        Integer spentAmount
) {
}
