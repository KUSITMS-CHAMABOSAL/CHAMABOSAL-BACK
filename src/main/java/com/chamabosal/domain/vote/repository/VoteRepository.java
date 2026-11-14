package com.chamabosal.domain.vote.repository;

import com.chamabosal.domain.vote.entity.Vote;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface VoteRepository extends JpaRepository<Vote, Long> {

    List<Vote> findByRound_Id(Long roundId);

    /** 결과 화면 득표 수용. 표를 받은 멤버를 함께 가져와 표마다 멤버를 따로 조회하지 않게 한다. */
    @Query("select v from Vote v left join fetch v.targetMember where v.round.id = :roundId")
    List<Vote> findWithTargetByRound_Id(Long roundId);

    boolean existsByRound_IdAndVoterMember_Id(Long roundId, Long voterMemberId);

    long countByRound_Id(Long roundId);
}
