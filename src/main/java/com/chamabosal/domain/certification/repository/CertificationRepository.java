package com.chamabosal.domain.certification.repository;

import com.chamabosal.domain.certification.entity.Certification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface CertificationRepository extends JpaRepository<Certification, Long> {

    boolean existsByRound_IdAndMember_IdAndDeletedAtIsNull(Long roundId, Long memberId);

    @Query("select c.member.id from Certification c where c.round.id = :roundId and c.deletedAt is null")
    List<Long> findCertifiedMemberIds(Long roundId);

    /** 후보 카드에 붙일 인증 내용을 한 번에 가져와서, 후보마다 인증을 따로 조회하지 않게 한다. */
    List<Certification> findByRound_IdAndDeletedAtIsNull(Long roundId);
}
