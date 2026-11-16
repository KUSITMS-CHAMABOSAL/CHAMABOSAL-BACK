package com.chamabosal.domain.pot.dto.response;

import com.chamabosal.domain.room.entity.BosalColor;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

public record PotCelebrationResponse(
        @Schema(description = "몇 번째 솥인지 (1부터)") int seq,
        @Schema(description = "이 솥의 목표 석") int goalAmount,
        @Schema(description = "멤버 전원이 받은 개인 공양미 (목표의 10%)") int rewardAmount,
        @Schema(description = "그 솥에 넣은 석이 많은 순. 넣은 기록이 있는 현재 멤버만") List<Contribution> contributions
) {

    public record Contribution(
            @Schema(description = "같은 석이면 같은 순위 (예: 1, 1, 3)") int rank,
            Long memberId,
            String nickname,
            BosalColor color,
            @Schema(description = "착용 아이템 코드, 상점 노출 순서") List<String> equippedItemCodes,
            @Schema(description = "그 솥에 넣은 석. 인증 삭제로 회수한 석은 뺌") int amount
    ) {
    }
}
