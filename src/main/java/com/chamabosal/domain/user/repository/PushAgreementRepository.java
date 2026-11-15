package com.chamabosal.domain.user.repository;

import com.chamabosal.domain.user.entity.PushAgreement;
import com.chamabosal.domain.user.entity.PushAgreementType;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface PushAgreementRepository extends JpaRepository<PushAgreement, Long> {

    boolean existsByUser_IdAndType(Long userId, PushAgreementType type);

    List<PushAgreement> findByUser_Id(Long userId);

    /** 내 데이터 삭제 (USER-05). */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from PushAgreement a where a.user.id = :userId")
    int deleteByUserId(Long userId);
}
