package com.chamabosal.domain.user.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;

public record UserSettingsUpdateRequest(
        @Schema(description = "콕 알림 토글 (POKE 알림에 적용). 빼면 그대로", example = "false")
        Boolean isPokePushEnabled,

        @Schema(description = "인증·투표 알림 토글 (RESULT·VOTE_OPENED·CERT_REMINDER 알림에 적용). 빼면 그대로", example = "true")
        Boolean isActivityPushEnabled
) {
}
