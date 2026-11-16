package com.chamabosal.domain.poke.repository;

import com.chamabosal.domain.poke.entity.Poke;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PokeRepository extends JpaRepository<Poke, Long> {

    Optional<Poke> findByRound_IdAndFromMember_IdAndToMember_Id(Long roundId, Long fromMemberId, Long toMemberId);
}
