package com.chamabosal.domain.room.service;

import com.chamabosal.domain.certification.photo.CertificationPhotoService;
import com.chamabosal.domain.certification.service.LeftMemberRecordCleaner;
import com.chamabosal.domain.certification.repository.CertificationRepository;
import com.chamabosal.domain.room.dto.response.LeavePreviewResponse;
import com.chamabosal.domain.round.repository.RoundRepository;
import com.chamabosal.domain.round.entity.RoundStatus;
import com.chamabosal.domain.round.entity.Round;
import com.chamabosal.domain.room.dto.request.RoomSeenResultUpdateRequest;
import com.chamabosal.domain.rice.service.RiceService;
import com.chamabosal.domain.room.dto.request.RoomCreateRequest;
import com.chamabosal.domain.room.dto.request.RoomNicknameUpdateRequest;
import com.chamabosal.domain.room.dto.request.RoomSeenPotUpdateRequest;
import com.chamabosal.domain.room.dto.response.RoomCreateResponse;
import com.chamabosal.domain.room.dto.response.RoomNewColorResponse;
import com.chamabosal.domain.room.entity.LeaveType;
import com.chamabosal.domain.room.entity.Room;
import com.chamabosal.domain.room.entity.RoomMember;
import com.chamabosal.domain.room.repository.RoomMemberRepository;
import com.chamabosal.domain.room.repository.RoomRepository;
import com.chamabosal.domain.tap.service.TapService;
import com.chamabosal.domain.user.entity.User;
import com.chamabosal.domain.user.repository.UserRepository;
import com.chamabosal.domain.vote.service.VoteCloseService;
import com.chamabosal.domain.vote.service.VoteStartService;
import com.chamabosal.global.common.exception.BaseErrorCode;
import com.chamabosal.global.common.exception.BaseException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class RoomService {

    public static final int MAX_JOINED_ROOMS = 2;
    private static final int ROOM_NAME_MAX_LENGTH = 12;
    private static final Pattern ROOM_NAME_PATTERN = Pattern.compile("[가-힣a-zA-Z0-9 ]+");
    private static final String ROOM_NAME_MESSAGE = "방 이름은 한글, 영문, 숫자, 띄어쓰기 1~12자예요.";
    private static final String SEEN_POT_MESSAGE = "본 완성 횟수가 방의 완성 횟수보다 커요.";
    private static final String SEEN_RESULT_MESSAGE = "이 방의 발표된 회차가 아니에요.";

    private final RoomRepository roomRepository;
    private final RoomMemberRepository roomMemberRepository;
    private final UserRepository userRepository;
    private final BosalColorPicker bosalColorPicker;
    private final VoteStartService voteStartService;
    private final VoteCloseService voteCloseService;
    private final TapService tapService;
    private final RiceService riceService;
    private final RoundRepository roundRepository;
    private final CertificationRepository certificationRepository;
    private final CertificationPhotoService certificationPhotoService;
    private final LeftMemberRecordCleaner leftMemberRecordCleaner;
    private final Clock clock;

    /** 새 방이라 겹칠 색이 없다. 저장하지 않아 부를 때마다 새로 뽑는다. */
    public RoomNewColorResponse drawNewColor() {
        return new RoomNewColorResponse(bosalColorPicker.pick(List.of()));
    }

    @Transactional
    public RoomCreateResponse create(Long userId, RoomCreateRequest request) {
        String name = validateRoomName(request.name());
        if (request.potGoalAmount() % RoomCreateRequest.POT_GOAL_UNIT != 0) {
            throw BaseException.withField(BaseErrorCode.INVALID_REQUEST, RoomCreateRequest.POT_GOAL_MESSAGE, "potGoalAmount");
        }
        // 토큰 검사 뒤에 내 데이터 삭제가 끝났을 수 있어 락을 잡은 뒤 다시 확인한다.
        User user = userRepository.findWithLockById(userId)
                .filter(found -> !found.isDeleted())
                .orElseThrow(() -> new BaseException(BaseErrorCode.UNAUTHENTICATED));
        if (roomMemberRepository.countByUser_IdAndLeftAtIsNullAndRoom_DeletedAtIsNull(userId) >= MAX_JOINED_ROOMS) {
            throw new BaseException(BaseErrorCode.ROOM_LIMIT_EXCEEDED);
        }

        // 사용자 행 락 안이라 동시 요청이 와도 첫 등록은 하나만 true다 (ROOM-01 14번).
        boolean isNewUser = !roomMemberRepository.existsByUser_Id(userId);
        Room room = roomRepository.save(Room.create(name, request.goal(), request.memberLimit(), request.potGoalAmount()));
        RoomMember owner = roomMemberRepository.save(RoomMember.create(
                room, user, request.nickname(), request.color(), LocalDateTime.now(clock)));
        room.assignOwner(owner.getId());
        return RoomCreateResponse.from(owner, isNewUser);
    }

    /**
     * 이 방에서 쓰는 내 닉네임을 바꾼다 (ROOM-04).
     * 투표 중인 회차가 있으면 막는다. 내보내기와 같이 03:00이 지났는데 배치가 아직 투표를 열지 않은 회차도 투표 시간으로 본다 (5번).
     * 지금 닉네임과 같으면 투표 중이어도 바꾸는 게 없어 성공한다 (4번).
     * 투표 시작과 겹쳐도 시작 뒤에 바뀌지 않도록 투표 시작과 같은 방 행 락을 잡고, 락 뒤에 커밋된 회차를 보도록 READ COMMITTED로 읽는다 (내보내기와 같음).
     */
    @Transactional(isolation = Isolation.READ_COMMITTED)
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
        if (roundRepository.existsVotingTime(room.getId(), Round.dateOf(LocalDateTime.now(clock)),
                RoundStatus.VOTING, RoundStatus.CERTIFYING)) {
            throw new BaseException(BaseErrorCode.NICKNAME_CHANGE_DURING_VOTE);
        }
        me.changeNickname(nickname);
    }

    /**
     * 본 결과 회차를 저장해 그 회차와 이전 회차를 모두 본 것으로 처리한다 (ROOM-06, RESULT-02 8번).
     * 이미 저장한 회차보다 이전 회차면 바꾸지 않는다. 비교와 저장을 한 문장으로 해서 동시 요청에도 뒤 회차가 남는다.
     */
    @Transactional
    public void markResultSeen(Long userId, Long roomId, RoomSeenResultUpdateRequest request) {
        Room room = roomRepository.findById(roomId)
                .filter(found -> !found.isDeleted())
                .orElseThrow(() -> new BaseException(BaseErrorCode.ROOM_NOT_FOUND));
        RoomMember me = roomMemberRepository.findByRoom_IdAndUser_IdAndLeftAtIsNull(room.getId(), userId)
                .orElseThrow(() -> new BaseException(BaseErrorCode.NOT_ROOM_MEMBER));
        Round round = roundRepository.findById(request.roundId())
                .filter(found -> found.getRoom().getId().equals(room.getId()))
                .filter(found -> found.getStatus() == RoundStatus.ANNOUNCED)
                .orElseThrow(() -> BaseException.withField(BaseErrorCode.INVALID_REQUEST, SEEN_RESULT_MESSAGE, "roundId"));
        roomMemberRepository.raiseSeenResultRoundDate(me.getId(), round.getRoundDate(), LocalDateTime.now(clock));
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

    /** 나가기 확인 화면의 사라질 공양미 (RICE-03 6번). 실제 차감과 같은 계산이고, 조회라 잠그지 않는다. */
    @Transactional(readOnly = true)
    public LeavePreviewResponse getLeavePreview(Long userId, Long roomId) {
        Room room = roomRepository.findById(roomId)
                .filter(found -> !found.isDeleted())
                .orElseThrow(() -> new BaseException(BaseErrorCode.ROOM_NOT_FOUND));
        RoomMember me = roomMemberRepository.findByRoom_IdAndUser_IdAndLeftAtIsNull(room.getId(), userId)
                .orElseThrow(() -> new BaseException(BaseErrorCode.NOT_ROOM_MEMBER));
        return new LeavePreviewResponse(riceService.calculateLeaveDeduction(room, me));
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
     * 내 데이터 삭제(USER-05 1번). 사용자 행 락을 잡은 트랜잭션에서 부른다. 참여 중인 방을 방 ID 순서로 하나씩 락을 잡아
     * 나가기와 같은 처리를 `DATA_DELETED`로 한다. 공양미 기록을 모두 지우므로 나가기 차감은 하지 않는다 (2번).
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void leaveAllForDataDeletion(Long userId) {
        // 락 전에 멤버 엔티티를 읽으면 락 뒤에도 캐시된 옛 상태를 봐서, 방 ID만 먼저 읽고 멤버는 방 락을 잡은 뒤 읽는다 (코드리뷰 반영).
        for (Long roomId : roomMemberRepository.findCurrentRoomIdsOrderById(userId)) {
            Room room = roomRepository.findWithLockById(roomId).orElseThrow();
            if (room.isDeleted()) {
                continue;
            }
            roomMemberRepository.findByRoom_IdAndUser_IdAndLeftAtIsNull(roomId, userId)
                    .ifPresent(member -> removeMember(room, member, LeaveType.DATA_DELETED));
        }
    }

    /**
     * 방장이 멤버를 내보낸다 (ROOM-08). 마지막 인증·03:00 배치의 투표 시작과 같은 방 행 락 안에서 투표 중인지 확인해서,
     * 투표가 먼저 시작됐으면 막고 내보내기가 먼저면 대상은 명단에 들어가지 않는다 (8번). 방장은 남아 있어 위임·삭제는 없다.
     * 내보내진 사람의 공양미도 차감해서 (RICE-03 8번, Q-94), 그 사람의 다른 방 나가기와 겹치지 않게 대상 사용자 행 → 방 순서로 잠근다.
     * 대상 사용자 ID는 락 전에 엔티티 없이 읽고, 그 조회가 스냅샷을 고정하지 않도록 READ COMMITTED로 읽는다. 대상이 현재 멤버인지는 방 락 뒤에 다시 본다.
     */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void kick(Long userId, Long roomId, Long targetMemberId) {
        roomMemberRepository.findKickTargetUserId(roomId, targetMemberId).ifPresent(userRepository::findWithLockById);
        Room room = roomRepository.findWithLockById(roomId)
                .filter(found -> !found.isDeleted())
                .orElseThrow(() -> new BaseException(BaseErrorCode.ROOM_NOT_FOUND));
        RoomMember me = roomMemberRepository.findByRoom_IdAndUser_IdAndLeftAtIsNull(room.getId(), userId)
                .orElseThrow(() -> new BaseException(BaseErrorCode.NOT_ROOM_MEMBER));
        if (!me.getId().equals(room.getOwnerMemberId())) {
            throw new BaseException(BaseErrorCode.NOT_ROOM_OWNER);
        }
        if (me.getId().equals(targetMemberId)) {
            throw new BaseException(BaseErrorCode.CANNOT_KICK_OWNER);
        }
        RoomMember target = roomMemberRepository.findById(targetMemberId)
                .filter(found -> found.getRoom().getId().equals(room.getId()) && found.isCurrent())
                .orElseThrow(() -> new BaseException(BaseErrorCode.MEMBER_NOT_FOUND));
        // 권한·대상을 먼저 보고 투표 중을 마지막에 봐서, 잘못된 요청은 회차 상태와 상관없이 같은 에러를 받는다.
        // 03:00이 지났는데 배치가 아직 투표를 열지 않은 회차도 투표 시간이라 막는다. 지금 내보내면 그 투표 명단에서 빠진다 (8번).
        if (roundRepository.existsVotingTime(room.getId(), Round.dateOf(LocalDateTime.now(clock)),
                RoundStatus.VOTING, RoundStatus.CERTIFYING)) {
            throw new BaseException(BaseErrorCode.KICK_DURING_VOTE);
        }
        removeMember(room, target, LeaveType.KICKED);
    }

    /**
     * 살아 있는 방의 행 락을 잡은 뒤 부른다.
     * 방이 남아 있으면 남은 멤버 기준으로 투표 시작을 확인한다 (ROOM-07 10번). 투표 마감(VOTE-04, ROOM-07 9번)도 여기에 붙인다.
     */
    void removeMember(Room room, RoomMember member, LeaveType leaveType) {
        LocalDateTime now = LocalDateTime.now(clock);
        // 스스로 나가거나 내보내지면 뺀다 (RICE-03, Q-94). 내 데이터 삭제는 공양미 기록을 모두 지워서 빼지 않는다 (USER-05 2번).
        if (leaveType == LeaveType.LEFT || leaveType == LeaveType.KICKED) {
            riceService.deductOnLeave(room, member);
        }
        deletePhotos(member, now);
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
        tapService.completeTapsIfAll(room.getId(), now);
    }

    /** 목표 달성 뒤 [더 수행하기] (ROOM-09 5번). 선택 대기를 끝내고 방은 그대로 둔다. */
    @Transactional
    public void continuePot(Long userId, Long roomId) {
        Room room = lockPotDecision(userId, roomId);
        room.continuePot();
    }

    /**
     * 목표 달성 뒤 [방 닫기] (ROOM-09 6·7번). 현재 멤버 전원을 나가게 하고 방을 삭제한다. 목표를 채운 방이라 나가기 차감은 없다.
     * 멤버를 남겨 두면 삭제된 방에 현재 멤버가 남아 기록 정리(CLEAN-01)와 내 데이터 삭제(USER-05)에서 빠져서 나간 것으로 처리한다.
     * 진행 중 회차는 마감·판정·알림 없이 멈추고, 사진·한 줄 기록·쓴 금액은 바로 지운다. 다른 멤버에게 푸시는 보내지 않는다 (Q-106).
     * 참여 방 수를 줄이기만 해서 멤버의 사용자 행은 잠그지 않는다.
     */
    @Transactional
    public void close(Long userId, Long roomId) {
        Room room = lockPotDecision(userId, roomId);
        closeLocked(room, LocalDateTime.now(clock));
    }

    /**
     * 방 행 락을 잡은 트랜잭션에서 방을 닫는다. [방 닫기]와 시연 리셋(DEMO-01 5번)이 같이 쓴다.
     * 현재 멤버 전원을 `ROOM_CLOSED`로 나가게 하고 방을 지운 뒤, 이 방에 있었던 멤버 전원의 사진·한 줄·쓴 금액을 바로 지운다.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void closeLocked(Room room, LocalDateTime now) {
        roomMemberRepository.findByRoom_IdAndLeftAtIsNull(room.getId())
                .forEach(member -> member.leave(now, LeaveType.ROOM_CLOSED));
        room.delete(now);
        // 다시 들어올 방이 없어 예전에 나간 멤버까지 이 방에 있었던 멤버 전원의 사진·한 줄·쓴 금액을 6개월을 기다리지 않고
        // 바로 지운다 (6번, CLEAN-01 6번, Q-106). 정리의 일괄 update가 위 나가기·삭제를 먼저 flush한 뒤 실행된다.
        leftMemberRecordCleaner.clear(roomMemberRepository.findIdsByRoomId(room.getId()), now);
    }

    /**
     * 방 행 락을 잡고 방장인지, 선택 대기인지 본다. 동시 요청은 먼저 처리된 쪽만 성공한다. 나중 요청은 [더 수행하기]가 먼저였으면
     * 선택 대기가 끝나 ROOM_011, [방 닫기]가 먼저였으면 방이 삭제돼 ROOM_005다 (ROOM-09 12번).
     */
    private Room lockPotDecision(Long userId, Long roomId) {
        Room room = roomRepository.findWithLockById(roomId)
                .filter(found -> !found.isDeleted())
                .orElseThrow(() -> new BaseException(BaseErrorCode.ROOM_NOT_FOUND));
        RoomMember me = roomMemberRepository.findByRoom_IdAndUser_IdAndLeftAtIsNull(room.getId(), userId)
                .orElseThrow(() -> new BaseException(BaseErrorCode.NOT_ROOM_MEMBER));
        if (!me.getId().equals(room.getOwnerMemberId())) {
            throw new BaseException(BaseErrorCode.NOT_ROOM_OWNER);
        }
        if (!room.isPotDecisionPending()) {
            throw new BaseException(BaseErrorCode.POT_DECISION_NOT_PENDING);
        }
        return room;
    }

    /**
     * 나가면 나간 유형과 상관없이 이 방의 사진을 바로 지운다 (ROOM-07 3번, ROOM-08 9번, ROOM-09 6번, Q-48). 일괄 update는 영속성 컨텍스트를
     * 비워 방장 위임·방 삭제 변경을 잃으므로 엔티티로 표시하고, 저장소에서는 커밋 뒤에 지운다. 내 데이터 삭제(USER-05)는 이어서
     * 예전에 나간 방의 남은 사진만 지운다.
     */
    private void deletePhotos(RoomMember member, LocalDateTime now) {
        certificationRepository.findByMember_IdAndImageKeyIsNotNullAndImageDeletedAtIsNull(member.getId()).stream()
                .map(certification -> certification.removeImage(now))
                .forEach(certificationPhotoService::deleteAfterCommit);
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
