package com.chamabosal.domain.store.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

public record EquippedItemsUpdateRequest(
        @Schema(description = "착용할 아이템 ID. 슬롯마다 하나씩 0~3개, 빈 배열이면 모두 벗음", example = "[1]")
        @NotNull(message = "착용할 아이템 목록을 보내 주세요.")
        @Size(max = 3, message = ITEM_COUNT_MESSAGE)
        List<@NotNull(message = "아이템 ID를 보내 주세요.") Long> itemIds
) {

    public static final String ITEM_COUNT_MESSAGE = "슬롯마다 하나씩 3개까지 착용할 수 있어요.";
}
