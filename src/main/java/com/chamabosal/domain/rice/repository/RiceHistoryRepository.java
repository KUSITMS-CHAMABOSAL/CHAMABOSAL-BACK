package com.chamabosal.domain.rice.repository;

import com.chamabosal.domain.rice.entity.RiceHistory;
import com.chamabosal.domain.rice.entity.RiceReason;
import com.chamabosal.domain.rice.entity.RiceRefType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;

public interface RiceHistoryRepository extends JpaRepository<RiceHistory, Long> {

    boolean existsByUser_IdAndReasonAndRefTypeAndRefId(Long userId, RiceReason reason, RiceRefType refType, Long refId);

    Optional<RiceHistory> findByUser_IdAndReasonAndRefTypeAndRefId(
            Long userId, RiceReason reason, RiceRefType refType, Long refId);

    @Query("select coalesce(sum(h.delta), 0) from RiceHistory h where h.user.id = :userId")
    long sumDeltaByUserId(Long userId);

    /** 그 방에서 마지막 나가기 차감 뒤에 쌓인 기록의 합. 다시 들어온 뒤 또 나가면 그 뒤에 모은 것만 센다 (RICE-03 2번). */
    @Query("""
            select coalesce(sum(h.delta), 0) from RiceHistory h
            where h.user.id = :userId and h.room.id = :roomId
              and h.id > coalesce((select max(l.id) from RiceHistory l
                                   where l.user.id = :userId and l.room.id = :roomId and l.reason = :leave), 0)
            """)
    long sumRoomDeltaSinceLastLeave(Long userId, Long roomId, RiceReason leave);
}
