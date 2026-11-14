package com.chamabosal.domain.round.repository;

import com.chamabosal.domain.round.entity.Round;
import com.chamabosal.domain.round.entity.RoundStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDate;
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

    /** 투표 탭 상태(VOTE-05) 판정용, 그 방의 가장 최근 회차 행 (상태 무관). */
    Optional<Round> findFirstByRoom_IdOrderByRoundDateDesc(Long roomId);
}
