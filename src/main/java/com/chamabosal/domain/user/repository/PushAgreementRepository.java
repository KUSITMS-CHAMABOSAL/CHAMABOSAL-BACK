package com.chamabosal.domain.user.repository;

import com.chamabosal.domain.user.entity.PushAgreement;
import com.chamabosal.domain.user.entity.PushAgreementType;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PushAgreementRepository extends JpaRepository<PushAgreement, Long> {

    boolean existsByUser_IdAndType(Long userId, PushAgreementType type);

    List<PushAgreement> findByUser_Id(Long userId);
}
