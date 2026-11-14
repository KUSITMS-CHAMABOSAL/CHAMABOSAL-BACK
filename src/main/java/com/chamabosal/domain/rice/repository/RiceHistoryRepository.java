package com.chamabosal.domain.rice.repository;

import com.chamabosal.domain.rice.entity.RiceHistory;
import com.chamabosal.domain.rice.entity.RiceReason;
import com.chamabosal.domain.rice.entity.RiceRefType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDateTime;
import java.util.Optional;

public interface RiceHistoryRepository extends JpaRepository<RiceHistory, Long> {

    boolean existsByUser_IdAndReasonAndRefTypeAndRefId(Long userId, RiceReason reason, RiceRefType refType, Long refId);

    Optional<RiceHistory> findByUser_IdAndReasonAndRefTypeAndRefId(
            Long userId, RiceReason reason, RiceRefType refType, Long refId);

    @Query("select coalesce(sum(h.delta), 0) from RiceHistory h where h.user.id = :userId")
    long sumDeltaByUserId(Long userId);

    /**
     * 그 방에서 이번에 들어온 뒤 쌓인 기록의 합. 다시 들어온 뒤 또 나가면 그 뒤에 모은 것만 센다 (RICE-03 2번).
     * 기록 시각(created_at)도 주입받은 Clock으로 찍혀 들어온 시각과 같은 기준이다.
     */
    @Query("""
            select coalesce(sum(h.delta), 0) from RiceHistory h
            where h.user.id = :userId and h.room.id = :roomId and h.createdAt >= :joinedAt
            """)
    long sumRoomDeltaSince(Long userId, Long roomId, LocalDateTime joinedAt);
}
