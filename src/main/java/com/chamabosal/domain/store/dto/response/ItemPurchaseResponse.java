package com.chamabosal.domain.store.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/** 아이템 구매 (STORE-02). */
public record ItemPurchaseResponse(
        @Schema(description = "산 뒤의 공양미 표시 잔액") int riceBalance,
        @Schema(description = "구매 뒤 착용 중인 아이템 ID 전체, 노출 순서. 산 아이템과 같은 슬롯에 있던 아이템은 빠짐")
        List<Long> equippedItemIds
) {
}
