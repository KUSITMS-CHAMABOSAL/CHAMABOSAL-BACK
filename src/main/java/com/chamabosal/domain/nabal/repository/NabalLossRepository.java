package com.chamabosal.domain.nabal.repository;

import com.chamabosal.domain.nabal.entity.NabalLoss;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface NabalLossRepository extends JpaRepository<NabalLoss, Long> {

    /** 아직 심지 않은(빠진) 나발 기록, 번호 순. */
    List<NabalLoss> findByUser_IdAndReplantedAtIsNullOrderByNabalNumber(Long userId);

    /** 여러 사용자의 빠진 나발 (오늘 탭 멤버 목록, ROOM-02 12번). */
    List<NabalLoss> findByUser_IdInAndReplantedAtIsNull(List<Long> userIds);

    boolean existsByUser_IdAndRoom_IdAndRoundDate(Long userId, Long roomId, LocalDate roundDate);

    @Query("""
            select count(l) > 0 from NabalLoss l
            where l.user.id = :userId and (l.seenAt is not null or l.replantedAt is not null)
            """)
    boolean existsSeenOrReplanted(Long userId);

    /** 보낸 기록 ID 이하의 안 본 기록을 본 것으로 처리한다 (NABAL-02 5번). 비교와 저장을 한 문장으로 한다. */
    @Modifying(clearAutomatically = true)
    @Query("""
            update NabalLoss l set l.seenAt = :now, l.updatedAt = :now
            where l.user.id = :userId and l.id <= :nabalLossId and l.seenAt is null
            """)
    int markSeenUpTo(Long userId, Long nabalLossId, LocalDateTime now);
}
