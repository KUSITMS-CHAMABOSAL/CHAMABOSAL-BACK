package com.chamabosal.domain.room.repository;

import com.chamabosal.domain.room.entity.LeaveType;
import com.chamabosal.domain.room.entity.Room;
import com.chamabosal.domain.room.entity.RoomMember;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface RoomMemberRepository extends JpaRepository<RoomMember, Long> {

    long countByUser_IdAndLeftAtIsNullAndRoom_DeletedAtIsNull(Long userId);

    Optional<RoomMember> findByRoom_IdAndUser_IdAndLeftAtIsNull(Long roomId, Long userId);

    List<RoomMember> findByRoom_IdAndLeftAtIsNull(Long roomId);

    /** 나발 판정(NABAL-01) 대상: 삭제하지 않았고 살아 있는 방에 참여 중인 사용자. */
    @Query("""
            select distinct m.user.id from RoomMember m
            where m.leftAt is null and m.room.deletedAt is null and m.user.deletedAt is null
            """)
    List<Long> findActiveUserIds();

    /** 내 데이터 삭제(USER-05)에서 나갈 방 ID. 락 순서를 맞추려고 방 ID 순으로 가져온다. */
    @Query("""
            select m.room.id from RoomMember m
            where m.user.id = :userId and m.leftAt is null and m.room.deletedAt is null
            order by m.room.id
            """)
    List<Long> findCurrentRoomIdsOrderById(Long userId);

    /** 내보내진 방 (USER-05 7번). 내보내진 시각과 상관없이, 아무도 들어갈 수 없는 삭제된 방은 뺀다. */
    @Query("""
            select m.room from RoomMember m
            where m.user.id = :userId and m.leaveType = :kicked and m.room.deletedAt is null
            """)
    List<Room> findRoomsByUserIdAndLeaveType(Long userId, LeaveType kicked);

    /** 내 데이터 삭제(USER-05)로 그 사용자의 모든 멤버 행과 사용자 연결을 끊는다. 기록은 "떠난 보살"로 남는다. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update RoomMember m set m.user = null where m.user.id = :userId")
    int detachUser(Long userId);

    /** 내 정보(USER-01)의 방 목록. 삭제되지 않은 방의 현재 멤버 행을 방과 함께, 들어온 순서로 가져온다. */
    @Query("""
            select m from RoomMember m join fetch m.room r
            where m.user.id = :userId and m.leftAt is null and r.deletedAt is null
            order by m.joinedAt, m.id
            """)
    List<RoomMember> findCurrentWithRoom(Long userId);

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

    /**
     * 본 결과 회차 날짜를 저장값보다 뒤일 때만 바꾼다 (ROOM-06 2·3번). raiseSeenPotCount와 같은 이유로 비교와 저장을 한 문장으로 한다.
     */
    @Modifying(clearAutomatically = true)
    @Query("""
            update RoomMember m set m.seenResultRoundDate = :roundDate, m.updatedAt = :now
            where m.id = :memberId and m.leftAt is null
              and (m.seenResultRoundDate is null or m.seenResultRoundDate < :roundDate)
            """)
    int raiseSeenResultRoundDate(Long memberId, LocalDate roundDate, LocalDateTime now);

    /** 같은 방에서 같은 닉네임(대소문자 무시)을 쓰는 멤버. 나간 멤버도 포함하고, excludeMemberId가 있으면 그 행은 뺀다. */
    @Query("""
            select m from RoomMember m
            where m.room.id = :roomId
              and (:excludeMemberId is null or m.id <> :excludeMemberId)
              and lower(m.nickname) = lower(:nickname)
            """)
    List<RoomMember> findSameNicknameMembers(Long roomId, String nickname, Long excludeMemberId);

    /**
     * 내보내기가 대상 사용자 행 락을 방 락보다 먼저 잡으려고, 엔티티 없이 대상의 사용자 ID만 구한다 (RICE-03 8번).
     * 대상 멤버 행이 이 방 것인지만 본다. 요청자의 멤버·방장 여부나 대상의 현재 멤버 여부를 조건에 넣으면 이 조회와 방 락 사이에
     * 그 상태가 바뀌어 락 없이 차감할 수 있어서다. 대상 행의 사용자는 바뀌지 않는다. 권한과 대상 확인은 방 락 뒤에 기존대로 한다.
     * 권한 없는 요청도 거절될 때까지 대상 행을 잠깐 잠글 수 있지만, 금액이 틀어지는 것보다 가볍다고 보고 받아들인다 (코드리뷰 반영).
     */
    @Query("select t.user.id from RoomMember t where t.id = :targetMemberId and t.room.id = :roomId")
    Optional<Long> findKickTargetUserId(Long roomId, Long targetMemberId);
}
