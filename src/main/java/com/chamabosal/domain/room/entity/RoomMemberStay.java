package com.chamabosal.domain.room.entity;

import com.chamabosal.global.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 다시 들어오기 전에 방에 있었던 구간. 다시 들어오면 멤버 행을 다시 써서(INVITE-03 6번) 들어온 시각이 덮어써지므로,
 * 덮어쓰기 전 구간을 남겨 둔다. 회차 내내 함께였는지(NABAL-01 11번) 판단에 쓴다.
 */
@Getter
@Entity
@Table(name = "room_member_stays", indexes = {
        @Index(name = "idx_room_member_stays_room_left", columnList = "room_id, left_at")
})
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RoomMemberStay extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "room_id", nullable = false)
    private Room room;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "room_member_id", nullable = false)
    private RoomMember member;

    @Column(nullable = false)
    private LocalDateTime joinedAt;

    @Column(nullable = false)
    private LocalDateTime leftAt;

    /** 나간 멤버의 지난 구간. 다시 들어오기 직전에 부른다. 현재 멤버는 끝난 구간이 없어 남길 수 없다. */
    public static RoomMemberStay of(RoomMember member) {
        if (member.isCurrent()) {
            throw new IllegalStateException("현재 멤버는 지난 구간을 남길 수 없습니다. memberId=" + member.getId());
        }
        RoomMemberStay stay = new RoomMemberStay();
        stay.room = member.getRoom();
        stay.member = member;
        stay.joinedAt = member.getJoinedAt();
        stay.leftAt = member.getLeftAt();
        return stay;
    }
}
