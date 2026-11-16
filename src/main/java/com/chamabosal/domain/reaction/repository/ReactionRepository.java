package com.chamabosal.domain.reaction.repository;

import com.chamabosal.domain.reaction.entity.Reaction;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface ReactionRepository extends JpaRepository<Reaction, Long> {

    Optional<Reaction> findByCertification_IdAndMember_Id(Long certificationId, Long memberId);

    /** 오늘 탭(ROOM-02 7번): 이 인증들에 내가 남긴 리액션. 다른 사람이 남긴 리액션은 읽지 않는다. */
    List<Reaction> findByMember_IdAndCertification_IdIn(Long memberId, Collection<Long> certificationIds);

    /** 오늘 탭(ROOM-02 7번): 인증마다 받은 리액션 수. 결과는 [인증 ID, 종류, 수]. 누가 눌렀는지는 읽지 않는다. */
    @Query("""
            select r.certification.id, r.type, count(r) from Reaction r
            where r.certification.id in :certificationIds
            group by r.certification.id, r.type
            """)
    List<Object[]> countByCertification(Collection<Long> certificationIds);
}
