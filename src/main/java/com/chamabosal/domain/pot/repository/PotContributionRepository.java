package com.chamabosal.domain.pot.repository;

import com.chamabosal.domain.pot.entity.PotContribution;
import com.chamabosal.domain.pot.entity.PotReason;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface PotContributionRepository extends JpaRepository<PotContribution, Long> {

    Optional<PotContribution> findByMember_IdAndReasonAndRefId(Long memberId, PotReason reason, Long refId);

    boolean existsByMember_IdAndReasonAndRefId(Long memberId, PotReason reason, Long refId);

    /** 넣은 순서대로. 같은 석이면 먼저 넣은 멤버를 앞에 두는 데 쓴다 (POT-01 15번). */
    @Query("select c from PotContribution c join fetch c.member where c.pot.id = :potId order by c.id")
    List<PotContribution> findWithMemberByPotId(Long potId);
}
