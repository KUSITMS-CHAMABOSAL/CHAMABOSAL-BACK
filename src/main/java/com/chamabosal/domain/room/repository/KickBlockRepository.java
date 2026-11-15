package com.chamabosal.domain.room.repository;

import com.chamabosal.domain.room.entity.KickBlock;
import java.time.LocalDateTime;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface KickBlockRepository extends JpaRepository<KickBlock, Long> {

    boolean existsByAnonKeyHashAndRoom_IdAndExpiresAtAfter(String anonKeyHash, Long roomId, LocalDateTime now);

    Optional<KickBlock> findByAnonKeyHashAndRoom_Id(String anonKeyHash, Long roomId);

    /** 만료된 차단 기록을 한 문장으로 지운다 (CLEAN-02). 지운 수를 돌려준다. */
    @Modifying
    @Query("delete from KickBlock b where b.expiresAt <= :now")
    int deleteExpired(LocalDateTime now);
}
