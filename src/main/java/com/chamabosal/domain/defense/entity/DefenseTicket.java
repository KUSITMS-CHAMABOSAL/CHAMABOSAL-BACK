package com.chamabosal.domain.defense.entity;

import com.chamabosal.domain.round.entity.Round;
import com.chamabosal.domain.user.entity.User;
import com.chamabosal.global.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 이마 방어권. 보유 개수·사용 횟수 제한이 없고, `used_at`이 비어 있으면 보유 중이다 (SHIELD-01·02). */
@Getter
@Entity
@Table(name = "defense_tickets",
        uniqueConstraints = @UniqueConstraint(name = "uk_defense_tickets_used_round", columnNames = "used_round_id"),
        indexes = @Index(name = "idx_defense_tickets_user_used", columnList = "user_id, used_at"))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DefenseTicket extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false)
    private LocalDateTime purchasedAt;

    private LocalDateTime usedAt;

    /** 같은 회차에 두 번 쓰지 않게 UNIQUE다. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "used_round_id")
    private Round usedRound;

    public static DefenseTicket purchase(User user, LocalDateTime purchasedAt) {
        DefenseTicket ticket = new DefenseTicket();
        ticket.user = user;
        ticket.purchasedAt = purchasedAt;
        return ticket;
    }

    public void use(Round round, LocalDateTime usedAt) {
        this.usedRound = round;
        this.usedAt = usedAt;
    }
}
