package com.chamabosal.domain.user.repository;

import com.chamabosal.domain.user.entity.PushAgreement;
import com.chamabosal.domain.user.entity.PushAgreementType;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PushAgreementRepository extends JpaRepository<PushAgreement, Long> {

    boolean existsByUser_IdAndType(Long userId, PushAgreementType type);
}
