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
}
