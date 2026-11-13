package com.chamabosal.domain.round.repository;

import com.chamabosal.domain.round.entity.Round;
import com.chamabosal.domain.round.entity.RoundStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface RoundRepository extends JpaRepository<Round, Long> {

    Optional<Round> findFirstByRoom_IdAndStatusOrderByRoundDateDesc(Long roomId, RoundStatus status);
}
