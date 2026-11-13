package com.chamabosal.domain.certification.repository;

import com.chamabosal.domain.certification.entity.Certification;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CertificationRepository extends JpaRepository<Certification, Long> {

    boolean existsByRound_IdAndMember_IdAndDeletedAtIsNull(Long roundId, Long memberId);
}
