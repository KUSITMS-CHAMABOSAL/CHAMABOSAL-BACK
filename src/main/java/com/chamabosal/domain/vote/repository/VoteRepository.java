package com.chamabosal.domain.vote.repository;

import com.chamabosal.domain.vote.entity.Vote;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface VoteRepository extends JpaRepository<Vote, Long> {

    List<Vote> findByRound_Id(Long roundId);
}
