package com.chamabosal.domain.certification.repository;

import com.chamabosal.domain.certification.entity.Certification;
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

    /** 인증 캘린더 (USER-02). 그 멤버 행의 삭제하지 않은 인증을 회차 날짜 순으로 가져온다. */
    @Query("""
            select c from Certification c join fetch c.round r
            where c.member.id = :memberId and c.deletedAt is null and r.roundDate between :from and :to
            order by r.roundDate
            """)
    List<Certification> findCalendar(Long memberId, LocalDate from, LocalDate to);

    /**
     * 떠난 보살 정리 후보 (CLEAN-01). 이미 비운 인증만 남은 멤버는 빠져서 정리가 끝난 멤버를 다시 읽지 않는다.
     * CERT-04로 사진 삭제를 더할 때 남은 사진도 후보 조건에 넣어야 사진만 남은 멤버가 빠지지 않는다.
     */
    @Query("""
            select distinct m from Certification c join c.member m
            where (c.memo is not null or c.spentAmount is not null) and m.leftAt < :leftBefore
            """)
    List<RoomMember> findMembersWithRecordsLeftBefore(LocalDateTime leftBefore);

    /** 일괄 update는 JPA 감사를 거치지 않아 updatedAt을 직접 쓴다. */
    @Modifying(clearAutomatically = true)
    @Query("""
            update Certification c set c.memo = null, c.spentAmount = null, c.updatedAt = :now
            where c.member.id in :memberIds and (c.memo is not null or c.spentAmount is not null)
            """)
    int clearRecordsOfMembers(Collection<Long> memberIds, LocalDateTime now);
}
