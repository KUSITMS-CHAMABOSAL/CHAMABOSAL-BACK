package com.chamabosal.domain.room.repository;

import com.chamabosal.domain.room.entity.LeaveType;
import com.chamabosal.domain.room.entity.RoomMember;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface RoomMemberRepository extends JpaRepository<RoomMember, Long> {

    long countByUser_IdAndLeftAtIsNullAndRoom_DeletedAtIsNull(Long userId);

    Optional<RoomMember> findByRoom_IdAndUser_IdAndLeftAtIsNull(Long roomId, Long userId);

    List<RoomMember> findByRoom_IdAndLeftAtIsNull(Long roomId);

    long countByRoom_IdAndLeftAtIsNull(Long roomId);

    Optional<RoomMember> findByInviteToken(String inviteToken);

    /** 엔티티를 읽지 않고 방 ID만 구한다. 방 행 락을 잡기 전에 읽은 멤버가 영속성 컨텍스트에 남지 않게 한다. */
    @Query("select m.room.id from RoomMember m where m.inviteToken = :inviteToken")
    Optional<Long> findRoomIdByInviteToken(String inviteToken);

    boolean existsByRoom_IdAndUser_IdAndLeaveType(Long roomId, Long userId, LeaveType leaveType);

    /** 이 방에서 가장 최근에 나간 내 행. 6개월 안이면 예전 닉네임을 보여주고 다시 들어올 때 이 행을 쓴다. */
    Optional<RoomMember> findFirstByRoom_IdAndUser_IdAndLeftAtIsNotNullOrderByLeftAtDesc(Long roomId, Long userId);

    /** 같은 방에서 같은 닉네임(대소문자 무시)을 쓰는 멤버. 나간 멤버도 포함하고, excludeMemberId가 있으면 그 행은 뺀다. */
    @Query("""
            select m from RoomMember m
            where m.room.id = :roomId
              and (:excludeMemberId is null or m.id <> :excludeMemberId)
              and lower(m.nickname) = lower(:nickname)
            """)
    List<RoomMember> findSameNicknameMembers(Long roomId, String nickname, Long excludeMemberId);
}
