package com.chamabosal.domain.user.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;

/** 앱 안 알림 토글 4종 (USER-03). 토스 알림 동의와 별개이고, 보낸 값만 바꾼다. */
public record UserSettingsUpdateRequest(
        @Schema(description = "콕 찌르기 알림 (POKE). 빼면 그대로", example = "false")
        Boolean isPokePushEnabled,
        @Schema(description = "투표 결과 알림 (RESULT). 빼면 그대로", example = "true")
        Boolean isResultPushEnabled,
        @Schema(description = "새 투표 알림 (VOTE_OPENED). 빼면 그대로", example = "true")
        Boolean isVoteOpenedPushEnabled,
        @Schema(description = "인증 마감 알림 (CERT_REMINDER). 빼면 그대로", example = "true")
        Boolean isCertReminderPushEnabled
) {
}
