package com.chamabosal.domain.room.repository;

import com.chamabosal.domain.room.entity.RoomMemberStay;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface RoomMemberStayRepository extends JpaRepository<RoomMemberStay, Long> {

    /** 나발 판정(NABAL-01 11번): 이 방에서 나 말고 [from, to)와 겹치는 지난 구간. */
    @Query("""
            select s from RoomMemberStay s
            where s.room.id = :roomId and s.member.id <> :memberId
              and s.joinedAt < :to and s.leftAt > :from
            """)
    List<RoomMemberStay> findOthersBetween(Long roomId, Long memberId, LocalDateTime from, LocalDateTime to);
}
