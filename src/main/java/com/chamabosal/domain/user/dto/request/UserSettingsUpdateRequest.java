package com.chamabosal.domain.user.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;

public record UserSettingsUpdateRequest(
        @Schema(description = "콕 알림. 빼면 그대로", example = "false")
        Boolean isPokePushEnabled,

        @Schema(description = "인증·투표 알림. 빼면 그대로", example = "true")
        Boolean isActivityPushEnabled
) {
}
