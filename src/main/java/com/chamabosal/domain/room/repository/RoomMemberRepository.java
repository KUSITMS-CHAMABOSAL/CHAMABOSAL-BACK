package com.chamabosal.domain.room.repository;

import com.chamabosal.domain.room.entity.RoomMember;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface RoomMemberRepository extends JpaRepository<RoomMember, Long> {

    long countByUser_IdAndLeftAtIsNullAndRoom_DeletedAtIsNull(Long userId);

    Optional<RoomMember> findByRoom_IdAndUser_IdAndLeftAtIsNull(Long roomId, Long userId);

    /** 같은 방에서 같은 닉네임(대소문자 무시)을 쓰는 멤버. 나간 멤버도 포함하고 내 행은 뺀다. */
    @Query("""
            select m from RoomMember m
            where m.room.id = :roomId
              and m.id <> :excludeMemberId
              and lower(m.nickname) = lower(:nickname)
            """)
    List<RoomMember> findSameNicknameMembers(Long roomId, String nickname, Long excludeMemberId);
}
