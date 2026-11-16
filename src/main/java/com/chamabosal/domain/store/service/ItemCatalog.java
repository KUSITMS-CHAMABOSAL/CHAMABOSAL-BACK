package com.chamabosal.domain.store.service;

import com.chamabosal.domain.store.entity.ItemSlot;
import java.util.List;

/**
 * 판매할 꾸미기 아이템 목록 (STORE-02 1번, Q-53). 디자인 데이터가 바뀌면 여기를 고치고, 앱이 시작할 때 code 기준으로 DB에 반영한다.
 * 목록에서 빼도 DB 행은 지우지 않아서 곳간에 계속 나오고 팔린다. 이미 산 사람의 보유 기록이 그 행을 가리킨다.
 * 판매를 멈추는 규칙은 명세에 없어 만들지 않았다.
 */
public final class ItemCatalog {

    public static final List<Entry> ENTRIES = List.of(
            new Entry("SANTA_HAT", "산타 모자", 100, ItemSlot.HAT, 1),
            new Entry("RUDOLPH_NOSE", "루돌프 코", 100, ItemSlot.FACE, 2)
    );

    private ItemCatalog() {
    }

    public record Entry(String code, String name, int price, ItemSlot slot, int displayOrder) {
    }
}
