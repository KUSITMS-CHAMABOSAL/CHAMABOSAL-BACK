package com.chamabosal.domain.store.entity;

import com.chamabosal.domain.user.entity.User;
import com.chamabosal.global.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 사용자가 산 아이템. 한 번 사면 영구 보유하고, 착용은 사용자 단위라 모든 방에 같다 (STORE-02 3번, STORE-03 5번). */
@Getter
@Entity
@Table(name = "user_items",
        uniqueConstraints = @UniqueConstraint(name = "uk_user_items_user_item", columnNames = {"user_id", "item_id"}))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserItem extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "item_id", nullable = false)
    private Item item;

    @Column(name = "is_equipped", nullable = false)
    private boolean equipped;

    /** 사면 바로 착용한다 (STORE-02 6번). */
    public static UserItem acquire(User user, Item item) {
        UserItem userItem = new UserItem();
        userItem.user = user;
        userItem.item = item;
        userItem.equipped = true;
        return userItem;
    }

    public void equip() {
        this.equipped = true;
    }

    public void unequip() {
        this.equipped = false;
    }
}
