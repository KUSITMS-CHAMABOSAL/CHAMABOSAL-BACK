package com.chamabosal.domain.tap.repository;

import com.chamabosal.domain.tap.entity.TapStart;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TapStartRepository extends JpaRepository<TapStart, Long> {

    boolean existsByRound_IdAndMember_Id(Long roundId, Long memberId);
}
