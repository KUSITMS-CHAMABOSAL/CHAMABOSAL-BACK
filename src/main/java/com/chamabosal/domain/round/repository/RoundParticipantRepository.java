package com.chamabosal.domain.round.repository;

import com.chamabosal.domain.round.entity.RoundParticipant;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface RoundParticipantRepository extends JpaRepository<RoundParticipant, Long> {

    List<RoundParticipant> findByRound_Id(Long roundId);
}
