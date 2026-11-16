package com.chamabosal.domain.store.entity;

import com.chamabosal.global.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 꾸미기 아이템 카탈로그. 목록과 가격은 디자인 데이터 기준이라 enum 대신 테이블에 둔다 (STORE-02 1번, Q-53). */
@Getter
@Entity
@Table(name = "items", uniqueConstraints = @UniqueConstraint(name = "uk_items_code", columnNames = "code"))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Item extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 클라이언트 이미지 매핑에 쓴다. */
    @Column(nullable = false, length = 50)
    private String code;

    @Column(nullable = false, length = 20)
    private String name;

    @Column(nullable = false)
    private int price;

    /** MySQL ENUM이나 값 목록 CHECK 없이 varchar로 만든다. 값을 추가해도 ddl-auto update가 막지 않는다. */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, columnDefinition = "varchar(20)")
    private ItemSlot slot;

    /** 상점 노출 순서 */
    @Column(nullable = false)
    private int displayOrder;

    public static Item create(String code, String name, int price, ItemSlot slot, int displayOrder) {
        Item item = new Item();
        item.code = code;
        item.update(name, price, slot, displayOrder);
        return item;
    }

    public void update(String name, int price, ItemSlot slot, int displayOrder) {
        this.name = name;
        this.price = price;
        this.slot = slot;
        this.displayOrder = displayOrder;
    }
}
