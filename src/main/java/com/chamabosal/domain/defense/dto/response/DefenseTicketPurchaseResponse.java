package com.chamabosal.domain.defense.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

public record DefenseTicketPurchaseResponse(
        @Schema(description = "산 뒤 표시 잔액. 실제 잔액이 음수면 0") int riceBalance
) {
}
