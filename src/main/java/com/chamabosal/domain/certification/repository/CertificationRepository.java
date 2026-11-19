package com.chamabosal.domain.certification.repository;

import com.chamabosal.domain.certification.entity.Certification;
import com.chamabosal.domain.certification.entity.CertificationResult;
import com.chamabosal.domain.room.entity.RoomMember;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface CertificationRepository extends JpaRepository<Certification, Long> {

    boolean existsByRound_IdAndMember_IdAndDeletedAtIsNull(Long roundId, Long memberId);

    /** 오늘 탭(ROOM-02 17번): 이 방의 기간 안 `참았어요` 수를 멤버별로. 삭제한 인증은 세지 않는다. 결과는 [멤버 ID, 수]. */
    @Query("""
            select c.member.id, count(c) from Certification c
            where c.round.room.id = :roomId and c.result = :endured and c.deletedAt is null
              and c.round.roundDate between :from and :to
            group by c.member.id
            """)
    List<Object[]> countByMemberInPeriod(Long roomId, CertificationResult endured, LocalDate from, LocalDate to);

    /** 나발 판정(NABAL-01): 그 방·회차 날짜에 삭제하지 않은 내 인증이 있는지. */
    boolean existsByRound_Room_IdAndRound_RoundDateAndMember_IdAndDeletedAtIsNull(
            Long roomId, LocalDate roundDate, Long memberId);

    Optional<Certification> findByRound_IdAndMember_IdAndDeletedAtIsNull(Long roundId, Long memberId);

    /** 투표가 열리지 않은 회차에서 인증을 삭제한 멤버는 다시 인증할 수 있다 (CERT-01 10번). */
    boolean existsByRound_IdAndMember_IdAndDeletedAtIsNotNull(Long roundId, Long memberId);

    /** 엔티티를 읽지 않고 방 ID만 구한다. 방 행 락을 잡은 뒤에 인증을 읽기 위해서다. */
    @Query("select c.round.room.id from Certification c where c.id = :certificationId")
    Optional<Long> findRoomIdByCertificationId(Long certificationId);

    @Query("select c.member.id from Certification c where c.round.id = :roundId and c.deletedAt is null")
    List<Long> findCertifiedMemberIds(Long roundId);

    /** 후보 카드에 붙일 인증 내용을 한 번에 가져와서, 후보마다 인증을 따로 조회하지 않게 한다. */
    List<Certification> findByRound_IdAndDeletedAtIsNull(Long roundId);

    /** 떠난 보살 정리 후보 (CLEAN-01). 한 줄·금액·사진이 모두 비워진 멤버는 빠져서 정리가 끝난 멤버를 다시 읽지 않는다. */
    @Query("""
            select distinct m from Certification c join c.member m
            where (c.memo is not null or c.spentAmount is not null or (c.imageKey is not null and c.imageDeletedAt is null))
              and m.leftAt < :leftBefore
            """)
    List<RoomMember> findMembersWithRecordsLeftBefore(LocalDateTime leftBefore);

    /**
     * 일괄 update는 JPA 감사를 거치지 않아 updatedAt을 직접 쓴다. 후보를 고른 뒤 다시 들어온 멤버는 건드리지 않도록 나간 상태를 다시 본다.
     * 같은 트랜잭션에서 방금 나간 멤버(방 닫기)도 보도록 실행 전에 flush한다.
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            update Certification c set c.memo = null, c.spentAmount = null, c.updatedAt = :now
            where c.member.id in :memberIds and c.member.leftAt is not null
              and (c.memo is not null or c.spentAmount is not null)
            """)
    int clearRecordsOfMembers(Collection<Long> memberIds, LocalDateTime now);

    /** 떠난 보살 정리 (CLEAN-01): 아직 지우지 않은 사진 키. */
    @Query("""
            select c.imageKey from Certification c
            where c.member.id in :memberIds and c.member.leftAt is not null
              and c.imageKey is not null and c.imageDeletedAt is null
            """)
    List<String> findImageKeysOfMembers(Collection<Long> memberIds);

    /**
     * 떠난 보살 정리 (CLEAN-01): 읽어 둔 사진 키만 지운 것으로 남겨, 저장소에서 지울 키와 표시한 행이 어긋나지 않게 한다.
     * 저장소 삭제는 호출한 쪽이 커밋 뒤에 한다.
     */
    @Modifying(clearAutomatically = true)
    @Query("""
            update Certification c set c.imageDeletedAt = :now, c.updatedAt = :now
            where c.imageKey in :imageKeys and c.imageDeletedAt is null
            """)
    int markImagesDeleted(Collection<String> imageKeys, LocalDateTime now);

    /**
     * 7일 뒤 삭제 대상 사진 키 (CERT-04 5번). 기준 시각은 투표 마감 시각, `NO_VOTE` 확정 시각(vote_started_at, Q-18),
     * 마감되지 않은 회차는 방 삭제 시각(ROOM-07 11번)이다. cutoff는 지금 - 7일이고, 정각에도 지운다.
     */
    @Query("""
            select c.imageKey from Certification c join c.round r join r.room room
            where c.imageKey is not null and c.imageDeletedAt is null
              and (r.voteClosedAt <= :cutoff
                or (r.status = com.chamabosal.domain.round.entity.RoundStatus.NO_VOTE and r.voteStartedAt <= :cutoff)
                or (r.voteClosedAt is null and r.status <> com.chamabosal.domain.round.entity.RoundStatus.NO_VOTE
                    and room.deletedAt <= :cutoff))
            """)
    List<String> findExpiredImageKeys(LocalDateTime cutoff);

    /** 나가기·내보내기 (ROOM-07 3번, ROOM-08 9번): 그 멤버 행의 아직 지우지 않은 사진. 엔티티로 읽어 지운 것으로 표시한다. */
    List<Certification> findByMember_IdAndImageKeyIsNotNullAndImageDeletedAtIsNull(Long memberId);

    /** 내 데이터 삭제 (USER-05 4번): 예전에 나간 멤버 행까지 포함한 내 인증의 남은 사진 키. 멤버 행 연결을 끊기 전에 읽는다. */
    @Query("""
            select c.imageKey from Certification c
            where c.member.user.id = :userId and c.imageKey is not null and c.imageDeletedAt is null
            """)
    List<String> findImageKeysOfUser(Long userId);

    /** 인증 캘린더 (USER-02). 그 멤버 행의 삭제하지 않은 인증을 회차 날짜 순으로 가져온다. */
    @Query("""
            select c from Certification c join fetch c.round r
            where c.member.id = :memberId and c.deletedAt is null and r.roundDate between :from and :to
            order by r.roundDate
            """)
    List<Certification> findCalendar(Long memberId, LocalDate from, LocalDate to);
}
