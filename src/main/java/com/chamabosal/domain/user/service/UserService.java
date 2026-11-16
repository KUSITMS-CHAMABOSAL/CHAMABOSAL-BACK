package com.chamabosal.domain.user.service;

import com.chamabosal.domain.certification.photo.CertificationPhotoService;
import com.chamabosal.domain.certification.repository.CertificationRepository;
import com.chamabosal.domain.defense.repository.DefenseTicketRepository;
import com.chamabosal.domain.nabal.repository.NabalLossRepository;
import com.chamabosal.domain.nabal.service.NabalService;
import com.chamabosal.domain.rice.repository.RiceHistoryRepository;
import com.chamabosal.domain.room.entity.KickBlock;
import com.chamabosal.domain.room.entity.LeaveType;
import com.chamabosal.domain.room.entity.Room;
import com.chamabosal.domain.room.entity.RoomMember;
import com.chamabosal.domain.room.repository.KickBlockRepository;
import com.chamabosal.domain.room.repository.RoomMemberRepository;
import com.chamabosal.domain.room.service.RoomService;
import com.chamabosal.domain.round.entity.RoundStatus;
import com.chamabosal.domain.round.repository.RoundRepository;
import com.chamabosal.domain.store.repository.UserItemRepository;
import com.chamabosal.domain.store.service.StoreService;
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
import org.springframework.transaction.annotation.Isolation;
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
    private final RoomService roomService;
    private final KickBlockRepository kickBlockRepository;
    private final RiceHistoryRepository riceHistoryRepository;
    private final DefenseTicketRepository defenseTicketRepository;
    private final NabalLossRepository nabalLossRepository;
    private final NabalService nabalService;
    private final CertificationRepository certificationRepository;
    private final CertificationPhotoService certificationPhotoService;
    private final StoreService storeService;
    private final UserItemRepository userItemRepository;
    private final Clock clock;

    /**
     * 내 데이터 즉시 삭제 (USER-05, Q-102). 사용자 → 방 순서로 락을 잡아 같은 사용자의 방 만들기·입장·나가기와 하나씩 처리한다.
     * 식별키를 비우기 전에 내보내진 방의 차단 해시를 남긴다 (7번). 지우는 쿼리는 영속성 컨텍스트를 비우므로 사용자 변경을 먼저 한다.
     * 사진(4번)은 멤버 행 연결을 끊기 전에 읽어 지운 것으로 표시하고 저장소에서는 커밋 뒤에 지운다. 락 전 조회가 스냅샷을 고정하지 않도록 READ COMMITTED로 읽어서, 방 락 뒤에
     * 다른 사람이 막 들어오거나 나간 결과를 보고 방장 위임·방 삭제를 판단한다 (입장과 같은 이유, 코드리뷰 반영).
     */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void deleteData(Long userId) {
        User user = userRepository.findWithLockById(userId)
                .filter(found -> !found.isDeleted())
                .orElseThrow(() -> new BaseException(BaseErrorCode.UNAUTHENTICATED));
        LocalDateTime now = LocalDateTime.now(clock);
        roomService.leaveAllForDataDeletion(userId);
        String anonKey = user.getTossAnonKey();
        if (anonKey != null) {
            String hash = KickBlock.hash(anonKey);
            for (Room room : roomMemberRepository.findRoomsByUserIdAndLeaveType(userId, LeaveType.KICKED)) {
                kickBlockRepository.findByAnonKeyHashAndRoom_Id(hash, room.getId()).ifPresentOrElse(
                        block -> block.renew(now),
                        () -> kickBlockRepository.save(KickBlock.create(anonKey, room, now)));
            }
        }
        user.deleteData(now);
        riceHistoryRepository.deleteByUserId(userId);
        defenseTicketRepository.deleteByUserId(userId);
        userItemRepository.deleteByUserId(userId);
        nabalLossRepository.deleteByUserId(userId);
        pushAgreementRepository.deleteByUserId(userId);
        List<String> photoKeys = certificationRepository.findImageKeysOfUser(userId);
        if (!photoKeys.isEmpty()) {
            certificationRepository.markImagesDeleted(photoKeys, now);
            photoKeys.forEach(certificationPhotoService::deleteAfterCommit);
        }
        roomMemberRepository.detachUser(userId);
    }

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
        List<String> equippedItemCodes = storeService.getEquippedItemCodes(List.of(userId)).getOrDefault(userId, List.of());
        return UserMeResponse.of(user, agreed, equippedItemCodes, nabalService.getStatus(userId), rooms, canCreateRoom);
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
