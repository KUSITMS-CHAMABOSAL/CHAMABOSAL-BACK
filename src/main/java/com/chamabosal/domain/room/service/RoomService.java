package com.chamabosal.domain.room.service;

import com.chamabosal.domain.room.dto.request.RoomCreateRequest;
import com.chamabosal.domain.room.dto.request.RoomNicknameUpdateRequest;
import com.chamabosal.domain.room.dto.response.RoomCreateResponse;
import com.chamabosal.domain.room.entity.Room;
import com.chamabosal.domain.room.entity.RoomMember;
import com.chamabosal.domain.room.repository.RoomMemberRepository;
import com.chamabosal.domain.room.repository.RoomRepository;
import com.chamabosal.domain.user.entity.User;
import com.chamabosal.domain.user.repository.UserRepository;
import com.chamabosal.global.common.exception.BaseErrorCode;
import com.chamabosal.global.common.exception.BaseException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class RoomService {

    private static final int MAX_JOINED_ROOMS = 2;
    private static final int ROOM_NAME_MAX_LENGTH = 8;
    private static final Pattern ROOM_NAME_PATTERN = Pattern.compile("[가-힣a-zA-Z0-9 ]+");
    private static final String ROOM_NAME_MESSAGE = "방 이름은 한글, 영문, 숫자, 띄어쓰기 1~8자입니다.";
    /** 나간 지 이 기간이 지나지 않은 멤버의 닉네임은 쓸 수 없다. */
    private static final int NICKNAME_HOLD_MONTHS = 6;

    private final RoomRepository roomRepository;
    private final RoomMemberRepository roomMemberRepository;
    private final UserRepository userRepository;
    private final BosalColorPicker bosalColorPicker;
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
        LocalDateTime now = LocalDateTime.now(clock);
        boolean taken = roomMemberRepository.findSameNicknameMembers(room.getId(), nickname, me.getId()).stream()
                .anyMatch(member -> member.holdsNickname(now, NICKNAME_HOLD_MONTHS));
        if (taken) {
            throw BaseException.withField(BaseErrorCode.NICKNAME_DUPLICATED, "nickname");
        }
        me.changeNickname(nickname);
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
