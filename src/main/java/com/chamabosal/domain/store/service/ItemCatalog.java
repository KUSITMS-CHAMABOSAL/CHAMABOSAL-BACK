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
            new Entry("HEADSET", "헤드셋", 100, ItemSlot.HAT, 2),
            new Entry("RUDOLPH_HEADBAND", "루돌프 머리띠", 100, ItemSlot.HAT, 3),
            new Entry("GYARU_HAIRPIN", "갸루 머리핀", 100, ItemSlot.HAT, 4),
            new Entry("SUNGLASSES", "선글라스", 50, ItemSlot.FACE, 5),
            new Entry("EYEBROW_PIERCING", "눈썹 피어싱", 50, ItemSlot.FACE, 6),
            new Entry("LEOPARD_GLASSES", "호피 안경", 50, ItemSlot.FACE, 7),
            new Entry("SANTA_BEARD", "산타 수염", 50, ItemSlot.FACE, 8),
            new Entry("RUDOLPH_NOSE", "루돌프 코", 50, ItemSlot.FACE, 9),
            new Entry("DUCK_LIPS", "오리 입", 50, ItemSlot.FACE, 10),
            new Entry("BEAD_EARRING", "염주 귀걸이", 50, ItemSlot.FACE, 11),
            new Entry("JADE_RING", "옥반지", 100, ItemSlot.ACCESSORY, 12),
            new Entry("ENERGY_WAVE", "에너지파", 100, ItemSlot.ACCESSORY, 13)
    );

    private ItemCatalog() {
    }

    public record Entry(String code, String name, int price, ItemSlot slot, int displayOrder) {
    }
}
