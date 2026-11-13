package com.chamabosal.domain.room.service;

import com.chamabosal.domain.room.dto.request.InviteJoinRequest;
import com.chamabosal.domain.room.dto.response.InviteJoinResponse;
import com.chamabosal.domain.room.dto.response.InvitePreviewResponse;
import com.chamabosal.domain.room.dto.response.InvitePreviewResponse.RoomSummary;
import com.chamabosal.domain.room.dto.response.InviteTokenResponse;
import com.chamabosal.domain.room.entity.BosalColor;
import com.chamabosal.domain.room.entity.LeaveType;
import com.chamabosal.domain.room.entity.Room;
import com.chamabosal.domain.room.entity.RoomMember;
import com.chamabosal.domain.room.repository.RoomMemberRepository;
import com.chamabosal.domain.room.repository.RoomRepository;
import com.chamabosal.domain.round.entity.Round;
import com.chamabosal.domain.round.entity.RoundStatus;
import com.chamabosal.domain.round.repository.RoundRepository;
import com.chamabosal.domain.user.entity.User;
import com.chamabosal.domain.user.repository.UserRepository;
import com.chamabosal.global.common.exception.BaseErrorCode;
import com.chamabosal.global.common.exception.BaseException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class InviteService {

    private final RoomRepository roomRepository;
    private final RoomMemberRepository roomMemberRepository;
    private final UserRepository userRepository;
    private final RoundRepository roundRepository;
    private final RoomService roomService;
    private final BosalColorPicker bosalColorPicker;
    private final InviteTokenGenerator inviteTokenGenerator;
    private final Clock clock;

    /** 같은 멤버의 동시 요청에도 토큰이 하나만 생기도록 방 행 락 안에서 확인하고 만든다. */
    @Transactional
    public CreationResult<InviteTokenResponse> issueToken(Long userId, Long roomId) {
        Room room = roomRepository.findWithLockById(roomId)
                .filter(found -> !found.isDeleted())
                .orElseThrow(() -> new BaseException(BaseErrorCode.ROOM_NOT_FOUND));
        RoomMember me = roomMemberRepository.findByRoom_IdAndUser_IdAndLeftAtIsNull(room.getId(), userId)
                .orElseThrow(() -> new BaseException(BaseErrorCode.NOT_ROOM_MEMBER));

        if (me.getInviteToken() != null) {
            return new CreationResult<>(new InviteTokenResponse(me.getInviteToken()), false);
        }
        me.issueInviteToken(inviteTokenGenerator.generate());
        return new CreationResult<>(new InviteTokenResponse(me.getInviteToken()), true);
    }

    @Transactional(readOnly = true)
    public InvitePreviewResponse preview(Long userId, String token) {
        Optional<RoomMember> found = roomMemberRepository.findByInviteToken(token);
        if (found.isEmpty()) {
            return InvitePreviewResponse.unavailable();
        }
        RoomMember inviter = found.get();
        Room room = inviter.getRoom();
        InviteStatus status = judge(room, inviter, userId);
        if (status == InviteStatus.UNAVAILABLE) {
            return InvitePreviewResponse.unavailable();
        }

        int memberCount = (int) roomMemberRepository.countByRoom_IdAndLeftAtIsNull(room.getId());
        String inviterNickname = inviter.isCurrent() ? inviter.getNickname() : null;
        String previousNickname = status == InviteStatus.ALREADY_MEMBER ? null
                : findRejoinableMember(room.getId(), userId).map(RoomMember::getNickname).orElse(null);
        return new InvitePreviewResponse(status, RoomSummary.from(room, memberCount), inviterNickname, previousNickname);
    }

    /**
     * 사용자 → 방 순서로 락을 잡고 미리보기와 같은 순서로 다시 판단한다.
     * MySQL 기본 격리 수준(REPEATABLE READ)은 첫 조회 시점의 스냅샷을 계속 읽는다.
     * 토큰으로 방을 찾는 조회가 락보다 먼저라서, 락을 잡은 뒤 다른 요청이 넣은 멤버를 보도록 READ COMMITTED로 읽는다.
     */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public CreationResult<InviteJoinResponse> join(Long userId, String token, InviteJoinRequest request) {
        Long roomId = roomMemberRepository.findRoomIdByInviteToken(token)
                .orElseThrow(() -> new BaseException(BaseErrorCode.ROOM_UNAVAILABLE));
        // 토큰 검사 뒤에 내 데이터 삭제가 끝났을 수 있어 락을 잡은 뒤 다시 확인한다.
        User user = userRepository.findWithLockById(userId)
                .filter(found -> !found.isDeleted())
                .orElseThrow(() -> new BaseException(BaseErrorCode.UNAUTHENTICATED));
        Room room = roomRepository.findWithLockById(roomId)
                .orElseThrow(() -> new BaseException(BaseErrorCode.ROOM_UNAVAILABLE));
        RoomMember inviter = roomMemberRepository.findByInviteToken(token)
                .orElseThrow(() -> new BaseException(BaseErrorCode.ROOM_UNAVAILABLE));

        return switch (judge(room, inviter, userId)) {
            case UNAVAILABLE -> throw new BaseException(BaseErrorCode.ROOM_UNAVAILABLE);
            case ROOM_LIMIT -> throw new BaseException(BaseErrorCode.ROOM_LIMIT_EXCEEDED);
            case ROOM_FULL -> throw new BaseException(BaseErrorCode.ROOM_FULL);
            case ALREADY_MEMBER -> new CreationResult<>(InviteJoinResponse.from(
                    roomMemberRepository.findByRoom_IdAndUser_IdAndLeftAtIsNull(room.getId(), userId).orElseThrow()), false);
            case JOINABLE -> new CreationResult<>(InviteJoinResponse.from(enter(room, user, request.nickname())), true);
        };
    }

    /** INVITE-02 처리 규칙 1번의 순서대로 판단하고 처음 걸린 상태를 돌려준다. */
    private InviteStatus judge(Room room, RoomMember inviter, Long userId) {
        if (room.isDeleted() || inviter.isKicked()) {
            return InviteStatus.UNAVAILABLE;
        }
        if (roomMemberRepository.findByRoom_IdAndUser_IdAndLeftAtIsNull(room.getId(), userId).isPresent()) {
            return InviteStatus.ALREADY_MEMBER;
        }
        if (roomMemberRepository.existsByRoom_IdAndUser_IdAndLeaveType(room.getId(), userId, LeaveType.KICKED)) {
            return InviteStatus.UNAVAILABLE;
        }
        if (roomMemberRepository.countByUser_IdAndLeftAtIsNullAndRoom_DeletedAtIsNull(userId)
                >= RoomService.MAX_JOINED_ROOMS) {
            return InviteStatus.ROOM_LIMIT;
        }
        if (roomMemberRepository.countByRoom_IdAndLeftAtIsNull(room.getId()) >= room.getMemberLimit()) {
            return InviteStatus.ROOM_FULL;
        }
        return InviteStatus.JOINABLE;
    }

    /** 나간 지 6개월이 안 됐으면 예전 행을 다시 쓰고, 아니면 새 행을 만든다. */
    private RoomMember enter(Room room, User user, String nickname) {
        Optional<RoomMember> previous = findRejoinableMember(room.getId(), user.getId());
        roomService.checkNicknameAvailable(room.getId(), nickname, previous.map(RoomMember::getId).orElse(null));

        List<BosalColor> usedColors = roomMemberRepository.findByRoom_IdAndLeftAtIsNull(room.getId()).stream()
                .map(RoomMember::getColor)
                .toList();
        LocalDateTime now = LocalDateTime.now(clock);
        RoomMember member;
        if (previous.isPresent()) {
            member = previous.get();
            BosalColor color = usedColors.contains(member.getColor()) ? bosalColorPicker.pick(usedColors) : member.getColor();
            member.rejoin(nickname, color, now);
        } else {
            member = roomMemberRepository.save(
                    RoomMember.create(room, user, nickname, bosalColorPicker.pick(usedColors), now));
        }
        member.fillSeenResult(roundRepository.findFirstByRoom_IdAndStatusOrderByRoundDateDesc(
                room.getId(), RoundStatus.ANNOUNCED).map(Round::getRoundDate).orElse(null));
        return member;
    }

    private Optional<RoomMember> findRejoinableMember(Long roomId, Long userId) {
        LocalDateTime now = LocalDateTime.now(clock);
        return roomMemberRepository.findFirstByRoom_IdAndUser_IdAndLeftAtIsNotNullOrderByLeftAtDesc(roomId, userId)
                .filter(member -> member.canRejoin(now));
    }
}
