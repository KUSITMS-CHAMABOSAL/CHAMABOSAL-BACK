package com.chamabosal.domain.room.entity;

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
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.HexFormat;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 내 데이터를 삭제한 사람이 다시 가입해 내보내진 방에 들어오는 것을 막는 기록 (USER-05 7번, Q-51).
 * 원래 식별키는 남기지 않고 SHA-256 해시만 남긴다. 토스 익명키는 추측할 수 없는 값이라 해시만으로 되돌릴 수 없다.
 */
@Getter
@Entity
@Table(name = "kick_blocks", uniqueConstraints = {
        @UniqueConstraint(name = "uk_kick_blocks_hash_room", columnNames = {"anon_key_hash", "room_id"})
})
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class KickBlock extends BaseEntity {

    private static final int RETENTION_MONTHS = 6;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, columnDefinition = "char(64)")
    private String anonKeyHash;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "room_id", nullable = false)
    private Room room;

    @Column(nullable = false)
    private LocalDateTime expiresAt;

    public static KickBlock create(String anonKey, Room room, LocalDateTime deletedAt) {
        KickBlock block = new KickBlock();
        block.anonKeyHash = hash(anonKey);
        block.room = room;
        block.expiresAt = deletedAt.plusMonths(RETENTION_MONTHS);
        return block;
    }

    /** 만료된 기록이 아직 지워지지 않았는데 다시 내보내진 뒤 삭제하면, 새 행 대신 이 행의 기한을 다시 6개월로 늘린다. */
    public void renew(LocalDateTime deletedAt) {
        this.expiresAt = deletedAt.plusMonths(RETENTION_MONTHS);
    }

    public static String hash(String anonKey) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(anonKey.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256을 쓸 수 없습니다.", e);
        }
    }
}
