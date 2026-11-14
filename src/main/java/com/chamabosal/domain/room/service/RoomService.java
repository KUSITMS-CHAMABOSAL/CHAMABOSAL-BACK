package com.chamabosal.domain.room.service;

import com.chamabosal.domain.rice.service.RiceService;
import com.chamabosal.domain.room.dto.request.RoomCreateRequest;
import com.chamabosal.domain.room.dto.request.RoomNicknameUpdateRequest;
import com.chamabosal.domain.room.dto.request.RoomSeenPotUpdateRequest;
import com.chamabosal.domain.room.dto.response.RoomCreateResponse;
import com.chamabosal.domain.room.entity.LeaveType;
import com.chamabosal.domain.room.entity.Room;
import com.chamabosal.domain.room.entity.RoomMember;
import com.chamabosal.domain.room.repository.RoomMemberRepository;
import com.chamabosal.domain.room.repository.RoomRepository;
import com.chamabosal.domain.user.entity.User;
import com.chamabosal.domain.user.repository.UserRepository;
import com.chamabosal.domain.vote.service.VoteCloseService;
import com.chamabosal.domain.vote.service.VoteStartService;
import com.chamabosal.global.common.exception.BaseErrorCode;
import com.chamabosal.global.common.exception.BaseException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class RoomService {

    static final int MAX_JOINED_ROOMS = 2;
    private static final int ROOM_NAME_MAX_LENGTH = 8;
    private static final Pattern ROOM_NAME_PATTERN = Pattern.compile("[가-힣a-zA-Z0-9 ]+");
    private static final String ROOM_NAME_MESSAGE = "방 이름은 한글, 영문, 숫자, 띄어쓰기 1~8자입니다.";
    private static final String SEEN_POT_MESSAGE = "본 완성 횟수가 방의 완성 횟수보다 큽니다.";

    private final RoomRepository roomRepository;
    private final RoomMemberRepository roomMemberRepository;
    private final UserRepository userRepository;
    private final BosalColorPicker bosalColorPicker;
    private final VoteStartService voteStartService;
    private final VoteCloseService voteCloseService;
    private final RiceService riceService;
    private final Clock clock;

    @Transactional
    public RoomCreateResponse create(Long userId, RoomCreateRequest request) {
        String name = validateRoomName(request.name());
        // 토큰 검사 뒤에 내 데이터 삭제가 끝났을 수 있어 락을 잡은 뒤 다시 확인한다.
        User user = userRepository.findWithLockById(userId)
                .filter(found -> !found.isDeleted())
                .orElseThrow(() -> new BaseException(BaseErrorCode.UNAUTHENTICATED));
        if (roomMemberRepository.countByUser_IdAndLeftAtIsNullAndRoom_DeletedAtIsNull(userId) >= MAX_JOINED_ROOMS) {
            throw new BaseException(BaseErrorCode.ROOM_LIMIT_EXCEEDED);
        }

        Room room = roomRepository.save(Room.create(name, request.goal(), request.memberLimit()));
        RoomMember owner = roomMemberRepository.save(RoomMember.create(
                room, user, request.nickname(), bosalColorPicker.pick(List.of()), LocalDateTime.now(clock)));
        room.assignOwner(owner.getId());
        return RoomCreateResponse.from(owner);
    }

    @Transactional
    public void updateNickname(Long userId, Long roomId, RoomNicknameUpdateRequest request) {
        Room room = roomRepository.findWithLockById(roomId)
                .filter(found -> !found.isDeleted())
                .orElseThrow(() -> new BaseException(BaseErrorCode.ROOM_NOT_FOUND));
        RoomMember me = roomMemberRepository.findByRoom_IdAndUser_IdAndLeftAtIsNull(room.getId(), userId)
                .orElseThrow(() -> new BaseException(BaseErrorCode.NOT_ROOM_MEMBER));

        String nickname = request.nickname();
        if (me.getNickname().equals(nickname)) {
            return;
        }
        checkNicknameAvailable(room.getId(), nickname, me.getId());
        me.changeNickname(nickname);
    }

    /** 완성 횟수는 늘기만 해서 락 없이 읽은 값과 비교해도 된다. 동시 요청은 조건부 update로 막아 방 행 락을 잡지 않는다. */
    @Transactional
    public void markPotSeen(Long userId, Long roomId, RoomSeenPotUpdateRequest request) {
        Room room = roomRepository.findById(roomId)
                .filter(found -> !found.isDeleted())
                .orElseThrow(() -> new BaseException(BaseErrorCode.ROOM_NOT_FOUND));
        RoomMember me = roomMemberRepository.findByRoom_IdAndUser_IdAndLeftAtIsNull(room.getId(), userId)
                .orElseThrow(() -> new BaseException(BaseErrorCode.NOT_ROOM_MEMBER));
        if (request.seenPotCount() > room.getPotCompletedCount()) {
            throw BaseException.withField(BaseErrorCode.INVALID_REQUEST, SEEN_POT_MESSAGE, "seenPotCount");
        }
        roomMemberRepository.raiseSeenPotCount(me.getId(), request.seenPotCount(), LocalDateTime.now(clock));
    }

    @Transactional
    public void leave(Long userId, Long roomId) {
        // 나가기 차감이 사용자 전체 잔액을 읽고 빼서, 같은 사람이 두 방을 동시에 나가지 않게 사용자 행을 먼저 잠근다.
        // 방 만들기·입장과 같은 사용자 → 방 순서다 (RICE-03).
        userRepository.findWithLockById(userId);
        Room room = roomRepository.findWithLockById(roomId)
                .filter(found -> !found.isDeleted())
                .orElseThrow(() -> new BaseException(BaseErrorCode.ROOM_NOT_FOUND));
        RoomMember me = roomMemberRepository.findByRoom_IdAndUser_IdAndLeftAtIsNull(room.getId(), userId)
                .orElseThrow(() -> new BaseException(BaseErrorCode.NOT_ROOM_MEMBER));
        removeMember(room, me, LeaveType.LEFT);
    }

    /**
     * 살아 있는 방의 행 락을 잡은 뒤 부른다. 내 데이터 삭제(USER-05)는 방마다 락을 잡는 공개 메서드를 이 클래스에 추가해 이 처리를 쓴다.
     * 방이 남아 있으면 남은 멤버 기준으로 투표 시작을 확인한다 (ROOM-07 10번). 투표 마감(VOTE-04, ROOM-07 9번)도 여기에 붙인다.
     */
    void removeMember(Room room, RoomMember member, LeaveType leaveType) {
        LocalDateTime now = LocalDateTime.now(clock);
        // 스스로 나갈 때만 뺀다. 내보내기는 Q-94가 정해지면, 내 데이터 삭제는 복구하면 공양미를 유지해서 빼지 않는다 (RICE-03).
        if (leaveType == LeaveType.LEFT) {
            riceService.deductOnLeave(room, member);
        }
        // 나가기를 저장하기 전에 찾아야 flush 시점과 상관없이 나가는 사람이 빠진다.
        Optional<RoomMember> successor = roomMemberRepository
                .findFirstByRoom_IdAndLeftAtIsNullAndIdNotOrderByJoinedAtAscIdAsc(room.getId(), member.getId());
        member.leave(now, leaveType);
        if (successor.isEmpty()) {
            room.delete(now);
            return;
        }
        if (member.getId().equals(room.getOwnerMemberId())) {
            room.assignOwner(successor.get().getId());
        }
        voteStartService.startIfAllCertified(room.getId(), now);
        voteCloseService.closeIfAllVoted(room.getId(), now);
    }

    /** 방 행 락을 잡은 뒤 부른다. 현재 멤버와 나간 지 6개월이 안 된 멤버의 닉네임은 대소문자 없이 비교해 막는다. */
    void checkNicknameAvailable(Long roomId, String nickname, Long excludeMemberId) {
        LocalDateTime now = LocalDateTime.now(clock);
        boolean taken = roomMemberRepository.findSameNicknameMembers(roomId, nickname, excludeMemberId).stream()
                .anyMatch(member -> member.holdsNickname(now));
        if (taken) {
            throw BaseException.withField(BaseErrorCode.NICKNAME_DUPLICATED, "nickname");
        }
    }

    /** 앞뒤 띄어쓰기를 지운 뒤 검사해야 해서 요청 DTO가 아니라 여기서 확인한다. */
    private String validateRoomName(String rawName) {
        String name = rawName.strip();
        if (name.isEmpty() || name.length() > ROOM_NAME_MAX_LENGTH || !ROOM_NAME_PATTERN.matcher(name).matches()) {
            throw BaseException.withField(BaseErrorCode.INVALID_REQUEST, ROOM_NAME_MESSAGE, "name");
        }
        return name;
    }
}
