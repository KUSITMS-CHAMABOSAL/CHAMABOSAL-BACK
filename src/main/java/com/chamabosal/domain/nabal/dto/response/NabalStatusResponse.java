package com.chamabosal.domain.nabal.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/** 내 보살의 나발 상태 (NABAL-02). 내 정보(USER-01)에 담는다. */
public record NabalStatusResponse(
        @Schema(description = "빠진 나발 번호(0~37), 오름차순. 비어 있으면 보살, 하나라도 있으면 중생") List<Integer> fallenNumbers,
        @Schema(description = "아직 빠지는 연출을 안 본 나발 번호. 있으면 앱 진입 때 연출") List<Integer> unseenFallenNumbers,
        @Schema(description = "가장 최근 나발 기록 ID. 연출을 본 뒤 seen-nabal-loss로 보냄. 기록이 없으면 null") Long lastNabalLossId,
        @Schema(description = "처음 빠졌는지. true면 보살 → 중생 회귀 스토리를 보여줌") boolean isFirstLoss
) {
}
