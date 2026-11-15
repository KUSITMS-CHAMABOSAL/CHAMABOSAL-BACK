package com.chamabosal.domain.tap.repository;

import com.chamabosal.domain.round.entity.RoundResultType;
import com.chamabosal.domain.tap.entity.Tap;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface TapRepository extends JpaRepository<Tap, Long> {

    boolean existsByRound_IdAndFromMember_Id(Long roundId, Long fromMemberId);

    /** 목탁 수행 대상: 그 회차에 1번 이상 받은 톡톡이 있는지 (Q-92 임시 결정). */
    boolean existsByRound_IdAndCountGreaterThan(Long roundId, int count);

    /** 목탁 수행에서 두드려야 하는 횟수: 그 회차에 받은 톡톡 합계. */
    @Query("select coalesce(sum(t.count), 0) from Tap t where t.round.id = :roundId")
    long sumCountByRoundId(Long roundId);

    /** 나를 뺀, 그 회차에 1번 이상 톡톡한 멤버 수 (`tappedMemberCount`). */
    long countByRound_IdAndCountGreaterThanAndFromMember_IdNot(Long roundId, int count, Long excludeMemberId);

    /** 받은 톡톡: 아직 재생하지 않았고 1번 이상인 것만. 보낸 사람을 함께 가져온다. */
    @Query("""
            select t from Tap t join fetch t.fromMember
            where t.round.id = :roundId and t.playedAt is null and t.count > 0
            order by t.id
            """)
    List<Tap> findUnplayedWithSender(Long roundId);

    /** 오늘 탭 진입 항목(ROOM-02 13번): 내가 지목된 PICKED 결과 중 아직 재생 안 한 받은 톡톡이 있는 회차, 최근 순. */
    @Query("""
            select distinct t.round.id, t.round.roundDate from Tap t
            where t.round.room.id = :roomId and t.round.pickedMember.id = :memberId and t.round.resultType = :picked
              and t.playedAt is null and t.count > 0
            order by t.round.roundDate desc
            """)
    List<Object[]> findUnplayedRoundIds(Long roomId, Long memberId, RoundResultType picked);

    /** 그때까지 받은 미재생 톡톡을 모두 재생됨으로 바꾼다 (RESULT-03 6번). 재시도해도 바뀌는 게 없다. */
    @Modifying(clearAutomatically = true)
    @Query("update Tap t set t.playedAt = :now, t.updatedAt = :now where t.round.id = :roundId and t.playedAt is null")
    int markPlayed(Long roundId, LocalDateTime now);
}
