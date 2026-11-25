package com.chamabosal.domain.defense.repository;

import com.chamabosal.domain.defense.entity.DefenseTicket;
import java.time.LocalDate;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface DefenseTicketRepository extends JpaRepository<DefenseTicket, Long> {

    long countByUser_IdAndUsedAtIsNull(Long userId);

    /** 곳간(STORE-01 4번): 미사용 방어권이 있는지. */

    /** 곳간(STORE-01 5번): 이 기간의 회차에 방어권을 썼는지. 기간은 회차 날짜 기준이다. */
    boolean existsByUser_IdAndUsedRound_RoundDateBetween(Long userId, LocalDate from, LocalDate to);

    /** 내 데이터 삭제 (USER-05). */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from DefenseTicket t where t.user.id = :userId")
    int deleteByUserId(Long userId);

    /** 보유 중인 것 중 가장 먼저 산 방어권. */
    Optional<DefenseTicket> findFirstByUser_IdAndUsedAtIsNullOrderByPurchasedAtAscIdAsc(Long userId);
}
