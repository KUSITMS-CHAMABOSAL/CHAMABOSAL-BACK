package com.chamabosal.domain.pot.repository;

import com.chamabosal.domain.pot.entity.RoomPot;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RoomPotRepository extends JpaRepository<RoomPot, Long> {

    /** 지금 쌓을 솥. 연장 전까지는 가장 최근 솥이다. */
    Optional<RoomPot> findFirstByRoom_IdOrderBySeqDesc(Long roomId);

    Optional<RoomPot> findByRoom_IdAndSeq(Long roomId, int seq);
}
