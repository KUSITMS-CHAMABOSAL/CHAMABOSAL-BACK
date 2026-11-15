package com.chamabosal.domain.user.service;

import com.chamabosal.domain.room.entity.RoomMember;
import com.chamabosal.domain.room.repository.RoomMemberRepository;
import com.chamabosal.domain.room.service.RoomService;
import com.chamabosal.domain.round.entity.RoundStatus;
import com.chamabosal.domain.round.repository.RoundRepository;
import com.chamabosal.domain.user.dto.request.PushAgreementCreateRequest;
import com.chamabosal.domain.user.dto.request.UserSettingsUpdateRequest;
import com.chamabosal.domain.user.dto.response.UserMeResponse;
import com.chamabosal.domain.user.dto.response.UserMeResponse.MyRoom;
import com.chamabosal.domain.user.entity.PushAgreement;
import com.chamabosal.domain.user.entity.PushAgreementType;
import com.chamabosal.domain.user.entity.User;
import com.chamabosal.domain.user.repository.PushAgreementRepository;
import com.chamabosal.domain.user.repository.UserRepository;
import com.chamabosal.global.common.exception.BaseErrorCode;
import com.chamabosal.global.common.exception.BaseException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class UserService {

    private static final String UNSUPPORTED_TYPE_MESSAGE = "동의를 받지 않는 알림 종류입니다.";

    private final UserRepository userRepository;
    private final PushAgreementRepository pushAgreementRepository;
    private final RoomMemberRepository roomMemberRepository;
    private final RoundRepository roundRepository;
    private final Clock clock;

    /**
     * 앱을 열 때 어느 화면으로 갈지 판단할 값 (USER-01). 방은 많아야 2개라 방마다 최근 발표 회차를 따로 읽는다.
     * 안 본 결과는 최근 발표 회차 날짜가 본 날짜보다 뒤일 때, 안 본 축하는 방의 완성 횟수가 본 횟수보다 클 때다 (RESULT-02, POT-01).
     */
    @Transactional(readOnly = true)
    public UserMeResponse getMe(Long userId) {
        User user = userRepository.findById(userId)
                .filter(found -> !found.isDeleted())
                .orElseThrow(() -> new BaseException(BaseErrorCode.UNAUTHENTICATED));
        Set<PushAgreementType> agreed = pushAgreementRepository.findByUser_Id(userId).stream()
                .map(PushAgreement::getType)
                .collect(Collectors.toSet());
        List<MyRoom> rooms = roomMemberRepository.findCurrentWithRoom(userId).stream()
                .map(member -> MyRoom.of(member, hasUnseen(member)))
                .toList();
        // 방 만들기·입장의 2개 제한과 같은 집계로 판단해 버튼과 실제 제한이 어긋나지 않게 한다.
        boolean canCreateRoom = roomMemberRepository.countByUser_IdAndLeftAtIsNullAndRoom_DeletedAtIsNull(userId)
                < RoomService.MAX_JOINED_ROOMS;
        return UserMeResponse.of(user, agreed, rooms, canCreateRoom);
    }

    private boolean hasUnseen(RoomMember member) {
        return member.hasUnseenPot() || roundRepository
                .findFirstByRoom_IdAndStatusOrderByRoundDateDesc(member.getRoom().getId(), RoundStatus.ANNOUNCED)
                .map(latest -> member.hasUnseenResult(latest.getRoundDate()))
                .orElse(false);
    }

    /** 두 토글을 동시에 따로 바꾸면 나중 요청이 먼저 바뀐 값을 덮어쓰지 않도록 사용자 행 락으로 하나씩 처리한다. */
    @Transactional
    public void updateSettings(Long userId, UserSettingsUpdateRequest request) {
        User user = userRepository.findWithLockById(userId)
                .filter(found -> !found.isDeleted())
                .orElseThrow(() -> new BaseException(BaseErrorCode.UNAUTHENTICATED));
        user.updatePushSettings(request.isPokePushEnabled(), request.isActivityPushEnabled());
    }

    /** 같은 요청이 동시에 와도 1건만 저장되도록 사용자 행 락 안에서 확인하고 저장한다. */
    @Transactional
    public void agreePush(Long userId, PushAgreementCreateRequest request) {
        PushAgreementType type = PushAgreementType.find(request.type())
                .orElseThrow(() -> BaseException.withField(BaseErrorCode.INVALID_REQUEST, UNSUPPORTED_TYPE_MESSAGE, "type"));
        User user = userRepository.findWithLockById(userId)
                .filter(found -> !found.isDeleted())
                .orElseThrow(() -> new BaseException(BaseErrorCode.UNAUTHENTICATED));
        if (request.isRejected() || pushAgreementRepository.existsByUser_IdAndType(userId, type)) {
            return;
        }
        pushAgreementRepository.save(PushAgreement.create(user, type, LocalDateTime.now(clock)));
    }
}
