package com.chamabosal.domain.room.service;

import com.chamabosal.domain.certification.photo.CertificationPhotoService;
import com.chamabosal.domain.certification.repository.CertificationRepository;
import com.chamabosal.domain.round.repository.RoundRepository;
import com.chamabosal.domain.round.entity.RoundStatus;
import com.chamabosal.domain.round.entity.Round;
import com.chamabosal.domain.room.dto.request.RoomSeenResultUpdateRequest;
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
    private static final int ROOM_NAME_MAX_LENGTH = 8;
    private static final Pattern ROOM_NAME_PATTERN = Pattern.compile("[가-힣a-zA-Z0-9 ]+");
    private static final String ROOM_NAME_MESSAGE = "방 이름은 한글, 영문, 숫자, 띄어쓰기 1~8자입니다.";
    private static final String SEEN_POT_MESSAGE = "본 완성 횟수가 방의 완성 횟수보다 큽니다.";
    private static final String SEEN_RESULT_MESSAGE = "이 방의 발표된 회차가 아닙니다.";

    private final RoomRepository roomRepository;
    private final RoomMemberRepository roomMemberRepository;
    private final UserRepository userRepository;
    private final BosalColorPicker bosalColorPicker;
    private final VoteStartService voteStartService;
    private final VoteCloseService voteCloseService;
    private final RiceService riceService;
    private final RoundRepository roundRepository;
    private final CertificationRepository certificationRepository;
    private final CertificationPhotoService certificationPhotoService;
    private final Clock clock;

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

        Room room = roomRepository.save(Room.create(name, request.goal(), request.memberLimit(), request.potGoalAmount()));
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
     * 공양미 차감은 Q-94가 정해질 때까지 하지 않는다 (removeMember의 LEFT 조건).
     */
    @Transactional
    public void kick(Long userId, Long roomId, Long targetMemberId) {
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
        // 스스로 나갈 때만 뺀다. 내보내기는 Q-94가 정해지면, 내 데이터 삭제는 공양미 기록을 모두 지워서 빼지 않는다 (RICE-03, USER-05 2번).
        if (leaveType == LeaveType.LEFT) {
            riceService.deductOnLeave(room, member);
        }
        // 나가면 나간 유형과 상관없이 이 방의 사진을 바로 지운다 (ROOM-07 3번, ROOM-08 9번, Q-48). 일괄 update는 영속성 컨텍스트를
        // 비워 방장 위임·방 삭제 변경을 잃으므로 엔티티로 표시하고, 저장소에서는 커밋 뒤에 지운다. 내 데이터 삭제(USER-05)는 이어서
        // 예전에 나간 방의 남은 사진만 지운다.
        certificationRepository.findByMember_IdAndImageKeyIsNotNullAndImageDeletedAtIsNull(member.getId()).stream()
                .map(certification -> certification.removeImage(now))
                .forEach(certificationPhotoService::deleteAfterCommit);
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
