package com.chamabosal.domain.certification.repository;

import com.chamabosal.domain.certification.entity.Certification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface CertificationRepository extends JpaRepository<Certification, Long> {

    boolean existsByRound_IdAndMember_IdAndDeletedAtIsNull(Long roundId, Long memberId);

    /** 투표가 열리지 않은 회차에서 인증을 삭제한 멤버는 다시 인증할 수 있다 (CERT-01 10번). */
    boolean existsByRound_IdAndMember_IdAndDeletedAtIsNotNull(Long roundId, Long memberId);

    /** 엔티티를 읽지 않고 방 ID만 구한다. 방 행 락을 잡은 뒤에 인증을 읽기 위해서다. */
    @Query("select c.round.room.id from Certification c where c.id = :certificationId")
    Optional<Long> findRoomIdByCertificationId(Long certificationId);

    @Query("select c.member.id from Certification c where c.round.id = :roundId and c.deletedAt is null")
    List<Long> findCertifiedMemberIds(Long roundId);

    /** 후보 카드에 붙일 인증 내용을 한 번에 가져와서, 후보마다 인증을 따로 조회하지 않게 한다. */
    List<Certification> findByRound_IdAndDeletedAtIsNull(Long roundId);
}
