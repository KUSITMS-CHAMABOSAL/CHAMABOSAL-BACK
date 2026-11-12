package com.chamabosal.domain.room.repository;

import com.chamabosal.domain.room.entity.LeaveType;
import com.chamabosal.domain.room.entity.RoomMember;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface RoomMemberRepository extends JpaRepository<RoomMember, Long> {

    long countByUser_IdAndLeftAtIsNullAndRoom_DeletedAtIsNull(Long userId);

    Optional<RoomMember> findByRoom_IdAndUser_IdAndLeftAtIsNull(Long roomId, Long userId);

    List<RoomMember> findByRoom_IdAndLeftAtIsNull(Long roomId);

    long countByRoom_IdAndLeftAtIsNull(Long roomId);

    /** 방장을 넘겨받을 멤버. 들어온 시각이 같으면 먼저 저장된 행. 다시 들어온 멤버는 예전 행이라 id만으로는 들어온 순서가 아니다. */
    Optional<RoomMember> findFirstByRoom_IdAndLeftAtIsNullAndIdNotOrderByJoinedAtAscIdAsc(Long roomId, Long excludeMemberId);

    Optional<RoomMember> findByInviteToken(String inviteToken);

    /** 엔티티를 읽지 않고 방 ID만 구한다. 방 행 락을 잡기 전에 읽은 멤버가 영속성 컨텍스트에 남지 않게 한다. */
    @Query("select m.room.id from RoomMember m where m.inviteToken = :inviteToken")
    Optional<Long> findRoomIdByInviteToken(String inviteToken);

    boolean existsByRoom_IdAndUser_IdAndLeaveType(Long roomId, Long userId, LeaveType leaveType);

    /** 이 방에서 가장 최근에 나간 내 행. 6개월 안이면 예전 닉네임을 보여주고 다시 들어올 때 이 행을 쓴다. */
    Optional<RoomMember> findFirstByRoom_IdAndUser_IdAndLeftAtIsNotNullOrderByLeftAtDesc(Long roomId, Long userId);

    /**
     * 저장값보다 클 때만 바꾼다. 비교와 저장을 한 문장으로 해서 동시 요청에서 작은 값이 큰 값을 덮어쓰지 않는다.
     * 그사이 나갔으면 바꾸지 않는다. 마지막 멤버가 나가 방이 삭제되는 경우도 여기서 걸린다.
     * bulk update는 auditing을 거치지 않아 updatedAt을 직접 넣고, 영속성 컨텍스트에 남은 옛 값을 비운다.
     */
    @Modifying(clearAutomatically = true)
    @Query("""
            update RoomMember m set m.seenPotCount = :seenPotCount, m.updatedAt = :now
            where m.id = :memberId and m.leftAt is null and m.seenPotCount < :seenPotCount
            """)
    int raiseSeenPotCount(Long memberId, int seenPotCount, LocalDateTime now);

    /** 같은 방에서 같은 닉네임(대소문자 무시)을 쓰는 멤버. 나간 멤버도 포함하고, excludeMemberId가 있으면 그 행은 뺀다. */
    @Query("""
            select m from RoomMember m
            where m.room.id = :roomId
              and (:excludeMemberId is null or m.id <> :excludeMemberId)
              and lower(m.nickname) = lower(:nickname)
            """)
    List<RoomMember> findSameNicknameMembers(Long roomId, String nickname, Long excludeMemberId);
}
