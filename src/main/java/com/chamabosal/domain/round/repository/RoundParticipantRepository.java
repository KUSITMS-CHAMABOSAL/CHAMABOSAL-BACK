package com.chamabosal.domain.round.repository;

import com.chamabosal.domain.round.entity.RoundParticipant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface RoundParticipantRepository extends JpaRepository<RoundParticipant, Long> {

    List<RoundParticipant> findByRound_Id(Long roundId);

    /** 투표권자만, 멤버를 함께 가져와서 호출하는 쪽에서 멤버를 하나씩 더 조회하지 않게 한다 (코드리뷰 반영). */
    @Query("select rp from RoundParticipant rp join fetch rp.member where rp.round.id = :roundId and rp.canVote = true")
    List<RoundParticipant> findCanVoteWithMemberByRound_Id(Long roundId);
}
