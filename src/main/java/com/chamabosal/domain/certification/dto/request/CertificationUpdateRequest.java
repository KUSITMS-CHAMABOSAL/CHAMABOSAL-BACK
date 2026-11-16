package com.chamabosal.domain.certification.dto.request;

import com.chamabosal.domain.certification.entity.CertificationResult;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

/** 등록과 같은 값을 모두 보내 통째로 바꾼다 (CERT-02 8번). 사진은 imageKey와 isImageKept로 바꾸기·유지·지우기를 고른다 (CERT-02 9·10번). */
public record CertificationUpdateRequest(
        @Schema(description = "참았어요(ENDURED) / 못 참았어요(FAILED)")
        @NotNull(message = "결과를 골라 주세요.")
        CertificationResult result,

        @Schema(description = "한 줄 기록 1~40자. 띄어쓰기만 있으면 안 됨", example = "치킨 반 마리만 시켰어요")
        @NotNull(message = "한 줄 기록을 입력해 주세요.")
        String memo,

        @Schema(description = "쓴 금액. FAILED면 필수 1~999,999, ENDURED면 저장된 금액을 지움", example = "23000")
        Integer spentAmount,

        @Schema(description = "새 사진의 키. 보내면 기존 사진을 바꾸고, isImageKept는 false여야 함", example = "tmp/12/2f1c8a7e-...")
        String imageKey,

        @Schema(description = "기존 사진 유지 여부. 필수. imageKey 없이 false면 사진을 지움", example = "true")
        @NotNull(message = "기존 사진 유지 여부를 입력해 주세요.")
        Boolean isImageKept
) {
}
