package com.chamabosal.domain.certification.dto.request;

import com.chamabosal.domain.certification.entity.CertificationResult;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

/** 글자 수와 금액은 결과에 따라 달라서 서비스에서 확인한다. 사진(imageKey)은 CERT-04에서 추가한다. */
public record CertificationCreateRequest(
        @Schema(description = "참았어요(ENDURED) / 못 참았어요(FAILED)")
        @NotNull(message = "결과를 골라 주세요.")
        CertificationResult result,

        @Schema(description = "한 줄 기록 1~40자. 띄어쓰기만 있으면 안 됨", example = "치킨 시켰어요")
        @NotNull(message = "한 줄 기록을 입력해 주세요.")
        String memo,

        @Schema(description = "쓴 금액. FAILED면 필수 1~999,999, ENDURED면 저장하지 않음", example = "23000")
        Integer spentAmount
) {
}
