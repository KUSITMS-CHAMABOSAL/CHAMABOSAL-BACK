package com.chamabosal.domain.tap.repository;

import com.chamabosal.domain.tap.entity.Tap;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface TapRepository extends JpaRepository<Tap, Long> {

    boolean existsByRound_IdAndFromMember_Id(Long roundId, Long fromMemberId);

    /** 그 회차에 톡톡을 보낸(건너뛰기 포함) 멤버 ID. 전원이 보냈는지 확인한다 (RESULT-03 11번). */
    @Query("select t.fromMember.id from Tap t where t.round.id = :roundId")
    List<Long> findFromMemberIdsByRoundId(Long roundId);

    /** 목탁 수행 대상: 그 회차에 1번 이상 받은 톡톡이 있는지 (Q-92 임시 결정). */
    boolean existsByRound_IdAndCountGreaterThan(Long roundId, int count);

    /** 목탁 수행에서 두드려야 하는 횟수: 그 회차에 받은 톡톡 합계. */
    @Query("select coalesce(sum(t.count), 0) from Tap t where t.round.id = :roundId")
    long sumCountByRoundId(Long roundId);

    /** 나를 뺀, 그 회차에 1번 이상 톡톡한 멤버 수 (`tappedMemberCount`). */
    long countByRound_IdAndCountGreaterThanAndFromMember_IdNot(Long roundId, int count, Long excludeMemberId);

    /** 받은 톡톡: 재생 여부와 상관없이 1번 이상인 것 전체. 보낸 사람을 함께 가져온다. */
    @Query("""
            select t from Tap t join fetch t.fromMember
            where t.round.id = :roundId and t.count > 0
            order by t.id
            """)
    List<Tap> findReceivedWithSender(Long roundId);
}
