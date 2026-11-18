package com.chamabosal.domain.room.dto.response;

import com.chamabosal.domain.room.entity.BosalColor;
import io.swagger.v3.oas.annotations.media.Schema;

public record RoomNewColorResponse(
        @Schema(description = "방 만들기 전에 뽑은 내 보살 색. 방을 만들 때 그대로 보냄") BosalColor color
) {
}
