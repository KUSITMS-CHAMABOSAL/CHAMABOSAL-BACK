package com.chamabosal.domain.round.repository;

import com.chamabosal.domain.round.entity.Round;
import com.chamabosal.domain.round.entity.RoundStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.Optional;

public interface RoundRepository extends JpaRepository<Round, Long> {

    Optional<Round> findFirstByRoom_IdAndStatusOrderByRoundDateDesc(Long roomId, RoundStatus status);

    Optional<Round> findByRoom_IdAndRoundDate(Long roomId, LocalDate roundDate);

    /** 인증도 콕도 없던 날은 행이 없어서, 어제가 아니라 그 전 가장 최근 회차를 찾는다. */
    Optional<Round> findFirstByRoom_IdAndRoundDateBeforeOrderByRoundDateDesc(Long roomId, LocalDate roundDate);
}
