package com.chamabosal.domain.store.dto.response;

import com.chamabosal.domain.store.entity.Item;
import com.chamabosal.domain.store.entity.ItemSlot;
import com.chamabosal.domain.store.entity.UserItem;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/** 곳간 (STORE-01). */
public record StoreResponse(
        @Schema(description = "공양미 표시 잔액. 실제 잔액이 음수면 0") int riceBalance,
        DefenseTicketStatus defenseTicket,
        @Schema(description = "상점 노출 순서") List<StoreItem> items
) {

    public record DefenseTicketStatus(
            @Schema(description = "미사용 방어권이 있으면 OWNED, 없으면 NONE") Status status,
            @Schema(description = "이번 주(회차 날짜 기준 월~일)에 방어권을 씀") boolean isUsedThisWeek
    ) {

        public enum Status {
            NONE,
            OWNED
        }
    }

    public record StoreItem(
            @Schema(description = "구매·착용 API에 씀") Long itemId,
            @Schema(description = "보살 캐릭터 이미지 매핑에 씀", example = "SANTA_HAT") String code,
            String name,
            int price,
            ItemSlot slot,
            boolean isOwned,
            boolean isEquipped
    ) {

        /** 보유하지 않았으면 owned가 null이다. */
        public static StoreItem of(Item item, UserItem owned) {
            return new StoreItem(item.getId(), item.getCode(), item.getName(), item.getPrice(), item.getSlot(),
                    owned != null, owned != null && owned.isEquipped());
        }
    }
}
