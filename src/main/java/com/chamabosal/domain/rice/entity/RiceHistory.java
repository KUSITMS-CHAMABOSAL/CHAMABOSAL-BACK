package com.chamabosal.domain.rice.entity;

import com.chamabosal.domain.room.entity.Room;
import com.chamabosal.domain.user.entity.User;
import com.chamabosal.global.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 개인 공양미 장부. 잔액은 저장하지 않고 이 기록의 합으로 계산한다 (RICE-01 11번). */
@Getter
@Entity
@Table(name = "rice_histories",
        uniqueConstraints = @UniqueConstraint(name = "uk_rice_histories_user_reason_ref",
                columnNames = {"user_id", "reason", "ref_type", "ref_id"}),
        indexes = @Index(name = "idx_rice_histories_user_room", columnList = "user_id, room_id"))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RiceHistory extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    /** 구매(`PURCHASE`)는 방이 없다. 나가기 차감(RICE-03)이 방별 합계에 쓴다. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "room_id")
    private Room room;

    @Column(nullable = false)
    private int delta;

    /** MySQL ENUM이나 값 목록 CHECK 없이 varchar로 만든다. 값을 추가해도 ddl-auto update가 막지 않는다. */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, columnDefinition = "varchar(20)")
    private RiceReason reason;

    @Enumerated(EnumType.STRING)
    @Column(columnDefinition = "varchar(20)")
    private RiceRefType refType;

    private Long refId;

    public static RiceHistory create(User user, Room room, int delta, RiceReason reason, RiceRefType refType, Long refId) {
        RiceHistory history = new RiceHistory();
        history.user = user;
        history.room = room;
        history.delta = delta;
        history.reason = reason;
        history.refType = refType;
        history.refId = refId;
        return history;
    }
}
