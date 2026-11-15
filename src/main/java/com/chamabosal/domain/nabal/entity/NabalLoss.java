package com.chamabosal.domain.nabal.entity;

import com.chamabosal.domain.room.entity.Room;
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
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 인증을 빠뜨린 방·회차마다 빠진 나발 한 개 (NABAL-01). 심으면 심은 시각을 남기고, 같은 번호가 다시 빠지면 새 기록이다.
 * 같은 사용자·방·회차 날짜는 한 번만 판정하도록 UNIQUE로 막는다 (9번).
 */
@Getter
@Entity
@Table(name = "nabal_losses",
        uniqueConstraints = @UniqueConstraint(name = "uk_nabal_losses_user_room_date",
                columnNames = {"user_id", "room_id", "round_date"}),
        indexes = @Index(name = "idx_nabal_losses_user_replanted", columnList = "user_id, replanted_at"))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class NabalLoss extends BaseEntity {

    public static final int NABAL_COUNT = 38;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "room_id", nullable = false)
    private Room room;

    @Column(nullable = false)
    private LocalDate roundDate;

    @Column(nullable = false)
    private int nabalNumber;

    private LocalDateTime seenAt;

    private LocalDateTime replantedAt;

    public static NabalLoss create(User user, Room room, LocalDate roundDate, int nabalNumber) {
        NabalLoss loss = new NabalLoss();
        loss.user = user;
        loss.room = room;
        loss.roundDate = roundDate;
        loss.nabalNumber = nabalNumber;
        return loss;
    }

    public boolean isFallen() {
        return replantedAt == null;
    }

    public void replant(LocalDateTime replantedAt) {
        this.replantedAt = replantedAt;
    }
}
