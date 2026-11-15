package com.chamabosal.domain.reaction.repository;

import com.chamabosal.domain.reaction.entity.Reaction;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReactionRepository extends JpaRepository<Reaction, Long> {

    Optional<Reaction> findByCertification_IdAndMember_Id(Long certificationId, Long memberId);
}
