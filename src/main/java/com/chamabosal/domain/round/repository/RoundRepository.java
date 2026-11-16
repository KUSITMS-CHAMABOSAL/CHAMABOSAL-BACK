package com.chamabosal.domain.round.repository;

import com.chamabosal.domain.round.entity.Round;
import com.chamabosal.domain.round.entity.RoundResultType;
import com.chamabosal.domain.round.entity.RoundStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface RoundRepository extends JpaRepository<Round, Long> {

    Optional<Round> findFirstByRoom_IdAndStatusOrderByRoundDateDesc(Long roomId, RoundStatus status);

    Optional<Round> findByRoom_IdAndRoundDate(Long roomId, LocalDate roundDate);

    /** 인증도 콕도 없던 날은 행이 없어서, 어제가 아니라 그 전 가장 최근 회차를 찾는다. */
    Optional<Round> findFirstByRoom_IdAndRoundDateBeforeOrderByRoundDateDesc(Long roomId, LocalDate roundDate);

    /** 03:00 배치 대상. 서버가 꺼져 있어 놓친 지난 회차도 함께 잡힌다. */
    List<Round> findByStatusAndRoundDateBefore(RoundStatus status, LocalDate roundDate);

    /** 15:00 배치 대상을 거르는 기준(회차 날짜 다음 날 15:00)은 자바에서 계산해서, 상태로만 먼저 가져온다. */
    List<Round> findByStatus(RoundStatus status);

    /** 엔티티를 읽지 않고 방 ID만 구한다. 방 행 락을 잡기 전에 읽은 회차가 영속성 컨텍스트에 남지 않게 한다. */
    @Query("select r.room.id from Round r where r.id = :roundId")
    Optional<Long> findRoomIdByRoundId(Long roundId);

    /**
     * 지목된 사람이 결과를 처음 조회한 시각을 비어 있을 때만 채운다 (RESULT-02 6번). 동시에 열어도 첫 시각 하나만 남고,
     * 회차의 버전을 올리지 않아 다른 회차 갱신과 부딪히지 않는다.
     */
    @Modifying(clearAutomatically = true)
    @Query("update Round r set r.pickedSeenAt = :now, r.updatedAt = :now where r.id = :roundId and r.pickedSeenAt is null")
    int markPickedSeen(Long roundId, LocalDateTime now);

    /**
     * 내보내기(ROOM-08)가 막을 회차가 있는지. 투표 중인 회차와, 인증이 마감됐는데 03:00 배치가 아직 투표를 열지 않은 회차를 함께 본다.
     * 오늘 탭(ROOM-02 18번)의 투표 중 회차 여부도 이 기준을 쓴다.
     */
    @Query("""
            select count(r) > 0 from Round r
            where r.room.id = :roomId
              and (r.status = :voting or (r.status = :certifying and r.roundDate < :currentRoundDate))
            """)
    boolean existsVotingTime(Long roomId, LocalDate currentRoundDate, RoundStatus voting, RoundStatus certifying);

    /** 이 회차보다 뒤 회차의 투표가 열렸는지. 투표 없음(`NO_VOTE`) 회차는 투표가 열린 게 아니다 (RESULT-03 4번). */
    boolean existsByRoom_IdAndRoundDateAfterAndStatusIn(Long roomId, LocalDate roundDate, List<RoundStatus> statuses);

    /** 오늘 탭(ROOM-02 17번): 이 방의 기간 안 지목 수를 멤버별로. 방어 성공(DEFENDED)은 세지 않는다. 결과는 [멤버 ID, 수]. */
    @Query("""
            select r.pickedMember.id, count(r) from Round r
            where r.room.id = :roomId and r.resultType = :picked and r.roundDate between :from and :to
            group by r.pickedMember.id
            """)
    List<Object[]> countPickedByMemberInPeriod(Long roomId, RoundResultType picked, LocalDate from, LocalDate to);

    /** 투표 탭 상태(VOTE-05) 판정용, 그 방의 가장 최근 회차 행 (상태 무관). */
    Optional<Round> findFirstByRoom_IdOrderByRoundDateDesc(Long roomId);
}
