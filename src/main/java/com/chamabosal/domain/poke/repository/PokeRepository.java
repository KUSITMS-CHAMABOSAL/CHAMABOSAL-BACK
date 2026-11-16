package com.chamabosal.domain.poke.repository;

import com.chamabosal.domain.poke.entity.Poke;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface PokeRepository extends JpaRepository<Poke, Long> {

    Optional<Poke> findByRound_IdAndFromMember_IdAndToMember_Id(Long roundId, Long fromMemberId, Long toMemberId);

    /** 오늘 탭(ROOM-02 9번): 이 회차에 내가 받은 콕 수. 보낸 사람은 돌려주지 않는다. */
    @Query("select coalesce(sum(p.count), 0) from Poke p where p.round.id = :roundId and p.toMember.id = :memberId")
    int sumReceived(Long roundId, Long memberId);
}
