package com.chamabosal.domain.defense.repository;

import com.chamabosal.domain.defense.entity.DefenseTicket;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface DefenseTicketRepository extends JpaRepository<DefenseTicket, Long> {

    long countByUser_IdAndUsedAtIsNull(Long userId);

    /** 내 데이터 삭제 (USER-05). */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from DefenseTicket t where t.user.id = :userId")
    int deleteByUserId(Long userId);

    /** 보유 중인 것 중 가장 먼저 산 방어권. */
    Optional<DefenseTicket> findFirstByUser_IdAndUsedAtIsNullOrderByPurchasedAtAscIdAsc(Long userId);
}
