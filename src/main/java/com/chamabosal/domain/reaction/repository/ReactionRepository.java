package com.chamabosal.domain.reaction.repository;

import com.chamabosal.domain.reaction.entity.Reaction;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReactionRepository extends JpaRepository<Reaction, Long> {

    Optional<Reaction> findByCertification_IdAndMember_Id(Long certificationId, Long memberId);

    /** 오늘 탭(ROOM-02 7번): 이 인증들에 내가 남긴 리액션. 다른 사람이 남긴 리액션은 읽지 않는다. */
    List<Reaction> findByMember_IdAndCertification_IdIn(Long memberId, Collection<Long> certificationIds);
}
