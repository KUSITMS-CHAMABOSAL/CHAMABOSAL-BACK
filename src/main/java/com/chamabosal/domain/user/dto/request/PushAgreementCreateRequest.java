package com.chamabosal.domain.user.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

/** enum으로 받으면 지원하지 않는 값이 JSON 변환 오류(COMMON_003)가 되어, 문자열로 받고 COMMON_002로 검사한다. type은 서비스가 PushAgreementType 기준으로 검사한다. */
public record PushAgreementCreateRequest(
        @Schema(description = "알림 종류. 지금은 CERT_REMINDER만 받음", example = "CERT_REMINDER")
        @NotNull(message = "알림 종류를 입력해 주세요.")
        String type,

        @Schema(description = "토스 알림 동의 결과 (앱인토스 값 그대로)",
                allowableValues = {"newAgreement", "alreadyAgreed", "agreementRejected"}, example = "newAgreement")
        @NotNull(message = "동의 결과를 입력해 주세요.")
        @Pattern(regexp = "newAgreement|alreadyAgreed|agreementRejected", message = "동의 결과가 올바르지 않습니다.")
        String result
) {

    public static final String REJECTED = "agreementRejected";

    public boolean isRejected() {
        return REJECTED.equals(result);
    }
}
