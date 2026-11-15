package com.chamabosal.domain.certification.repository;

import com.chamabosal.domain.certification.entity.Certification;
import com.chamabosal.domain.certification.entity.CertificationResult;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDate;
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

    /** 인증 캘린더 (USER-02). 그 멤버 행의 삭제하지 않은 인증을 회차 날짜 순으로 가져온다. */
    @Query("""
            select c from Certification c join fetch c.round r
            where c.member.id = :memberId and c.deletedAt is null and r.roundDate between :from and :to
            order by r.roundDate
            """)
    List<Certification> findCalendar(Long memberId, LocalDate from, LocalDate to);
}
