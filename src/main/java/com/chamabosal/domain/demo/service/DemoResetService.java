package com.chamabosal.domain.demo.service;

import com.chamabosal.domain.certification.entity.Certification;
import com.chamabosal.domain.certification.entity.CertificationResult;
import com.chamabosal.domain.certification.repository.CertificationRepository;
import com.chamabosal.domain.defense.repository.DefenseTicketRepository;
import com.chamabosal.domain.demo.dto.response.DemoResetResponse;
import com.chamabosal.domain.demo.service.DemoSettings.Slot;
import com.chamabosal.domain.nabal.repository.NabalLossRepository;
import com.chamabosal.domain.rice.entity.RiceHistory;
import com.chamabosal.domain.rice.entity.RiceReason;
import com.chamabosal.domain.rice.repository.RiceHistoryRepository;
import com.chamabosal.domain.room.entity.BosalColor;
import com.chamabosal.domain.room.entity.Room;
import com.chamabosal.domain.room.entity.RoomGoal;
import com.chamabosal.domain.room.entity.RoomMember;
import com.chamabosal.domain.room.repository.RoomMemberRepository;
import com.chamabosal.domain.room.repository.RoomRepository;
import com.chamabosal.domain.room.service.RoomService;
import com.chamabosal.domain.round.entity.Round;
import com.chamabosal.domain.round.entity.RoundParticipant;
import com.chamabosal.domain.round.entity.RoundResultType;
import com.chamabosal.domain.round.repository.RoundParticipantRepository;
import com.chamabosal.domain.round.repository.RoundRepository;
import com.chamabosal.domain.store.repository.UserItemRepository;
import com.chamabosal.domain.tap.entity.Tap;
import com.chamabosal.domain.tap.repository.TapRepository;
import com.chamabosal.domain.user.entity.User;
import com.chamabosal.domain.user.repository.UserRepository;
import com.chamabosal.domain.vote.entity.Vote;
import com.chamabosal.domain.vote.repository.VoteRepository;
import com.chamabosal.global.common.exception.BaseErrorCode;
import com.chamabosal.global.common.exception.BaseException;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 시연 리셋 (DEMO-01). 시연 번호 하나의 시연 계정을 방문객 맞이 전 상태로 되돌리고 시연 방 2개를 새로 만든다.
 * 시연 계정 사용자 행 락으로 같은 시연 번호의 리셋을 하나씩 처리한다 (13번). 시연 번호마다 계정과 가짜 팀원이 달라 다른 번호는 건드리지 않는다.
 */
@Service
@RequiredArgsConstructor
public class DemoResetService {

    private static final int START_RICE = 200;
    private static final int MEMBER_LIMIT = 4;
    private static final int POT_GOAL = 300;
    private static final BosalColor DEMO_ACCOUNT_COLOR = BosalColor.YELLOW;

    private final DemoSettings demoSettings;
    private final RoomService roomService;
    private final UserRepository userRepository;
    private final RoomRepository roomRepository;
    private final RoomMemberRepository roomMemberRepository;
    private final RoundRepository roundRepository;
    private final RoundParticipantRepository roundParticipantRepository;
    private final CertificationRepository certificationRepository;
    private final VoteRepository voteRepository;
    private final TapRepository tapRepository;
    private final RiceHistoryRepository riceHistoryRepository;
    private final NabalLossRepository nabalLossRepository;
    private final UserItemRepository userItemRepository;
    private final DefenseTicketRepository defenseTicketRepository;
    private final Clock clock;

    @Transactional
    public DemoResetResponse reset(String requestKey, int slotNumber) {
        demoSettings.checkAccess(requestKey);
        Slot slot = demoSettings.getSlot(slotNumber);
        Long userId = slot.userId();
        userRepository.findWithLockById(userId)
                .filter(found -> !found.isDeleted())
                .orElseThrow(() -> new BaseException(BaseErrorCode.DEMO_SLOT_NOT_FOUND));
        LocalDateTime now = LocalDateTime.now(clock);

        deleteDemoRooms(userId, now);
        // 아래 일괄 삭제는 영속성 컨텍스트를 비워서, 그 뒤에 사용자를 다시 읽는다. 사용자 행 락은 트랜잭션 끝까지 유지된다.
        riceHistoryRepository.deleteByUserId(userId);
        nabalLossRepository.deleteByUserId(userId);
        userItemRepository.deleteByUserId(userId);
        defenseTicketRepository.deleteByUserId(userId);

        User user = userRepository.findById(userId).orElseThrow();
        riceHistoryRepository.save(RiceHistory.create(user, null, START_RICE, RiceReason.DEMO_RESET, null, null));
        Map<DemoFakeMember, User> fakes = findOrCreateFakes(slot.number());

        Room mainRoom = createMainRoom(user, slot, fakes, now);
        Room moktakRoom = createMoktakRoom(user, slot, fakes, now);
        return new DemoResetResponse(mainRoom.getId(), moktakRoom.getId());
    }

    /**
     * 시연 계정이 참여 중인 방이 모두 시연 방일 때만 지운다. 일반 방이 하나라도 있으면 아무것도 바꾸지 않고 거부한다 (4·5번).
     * [방 닫기](ROOM-09)와 같은 처리로 멤버 전원을 나가게 하고 방을 지운 뒤, 방문객의 사진·한 줄·쓴 금액을 바로 지운다.
     * 나가기 차감은 하지 않는다.
     */
    private void deleteDemoRooms(Long userId, LocalDateTime now) {
        List<Room> rooms = roomMemberRepository.findCurrentWithRoom(userId).stream()
                .map(RoomMember::getRoom)
                .toList();
        boolean hasRegularRoom = rooms.stream().anyMatch(room -> !roomMemberRepository
                .existsByRoom_IdAndLeftAtIsNullAndUser_TossAnonKeyStartingWith(room.getId(), DemoSettings.FAKE_KEY_PREFIX));
        if (hasRegularRoom) {
            throw new BaseException(BaseErrorCode.DEMO_ACCOUNT_IN_REGULAR_ROOM);
        }
        rooms.stream().map(Room::getId).sorted().forEach(roomId ->
                roomService.closeLocked(roomRepository.findWithLockById(roomId).orElseThrow(), now));
    }

    /** 가짜 팀원은 시연 번호마다 한 번 만들고 다시 쓴다 (8번). 토스 식별키 UNIQUE가 중복 생성을 막는다. */
    private Map<DemoFakeMember, User> findOrCreateFakes(int slotNumber) {
        Map<DemoFakeMember, User> fakes = new EnumMap<>(DemoFakeMember.class);
        for (DemoFakeMember fake : DemoFakeMember.values()) {
            String key = fake.tossAnonKey(slotNumber);
            fakes.put(fake, userRepository.findByTossAnonKey(key)
                    .orElseGet(() -> userRepository.save(User.create(key))));
        }
        return fakes;
    }

    /** 시연 방 1: 지금 회차가 인증 중이고 가짜 팀원 3명만 인증해 있다 (10번). 시연 계정이 인증하면 전원 인증으로 투표가 시작된다. */
    private Room createMainRoom(User user, Slot slot, Map<DemoFakeMember, User> fakes, LocalDateTime now) {
        LocalDate today = Round.dateOf(now);
        DemoRoom demoRoom = createRoom(user, slot, fakes, now, today);
        Round round = roundRepository.save(Round.create(demoRoom.room(), today));
        demoRoom.fakeMembers().forEach((fake, member) -> certificationRepository.save(Certification.create(
                round, member, fake.result(), fake.memo(), fake.spentAmount(), now)));
        return demoRoom.room();
    }

    /**
     * 시연 방 2: 지난 회차에서 시연 계정이 지목됐고 톡톡 마감이 지나 받은 톡톡 10회가 전달된 상태다 (11번).
     * 실제 흐름(인증 → 투표 시작 → 전원 투표 → 발표)의 행을 그대로 만들고, 마감 시각을 5시간 1분 전으로 둬서 목탁 수행이 바로 열린다.
     * 결과는 본 것으로 두고 목탁 수행은 하지 않은 상태다. 지금 회차는 행이 없어 인증 중이다.
     */
    private Room createMoktakRoom(User user, Slot slot, Map<DemoFakeMember, User> fakes, LocalDateTime now) {
        LocalDate previous = Round.dateOf(now).minusDays(1);
        // 회차 시작(03:00) 정각이면 그 회차 날짜에 들어온 멤버로 보여서(CERT-05 2번) 1분 전으로 둔다 (코드리뷰 반영).
        LocalDateTime joinedAt = Round.startOf(previous).minusMinutes(1);
        LocalDateTime certifiedAt = joinedAt.plusHours(12);

        DemoRoom demoRoom = createRoom(user, slot, fakes, joinedAt, previous);
        RoomMember demoMember = demoRoom.demoMember();
        Round round = roundRepository.save(Round.create(demoRoom.room(), previous));
        // 톡톡 마감이 지나도록 5시간 1분 전에 마감하되, 그 회차 투표 마감 시각(다음 날 15:00)을 넘기지 않는다 (코드리뷰 반영).
        LocalDateTime closedAt = min(now.minusHours(Round.TAP_HOURS).minusMinutes(1), round.getVoteClosesAt());
        LocalDateTime startedAt = min(Round.startOf(previous.plusDays(1)), closedAt.minusMinutes(1));
        certificationRepository.save(Certification.create(
                round, demoMember, CertificationResult.ENDURED, "오늘도 잘 참았어요", null, certifiedAt));
        demoRoom.fakeMembers().forEach((fake, member) -> certificationRepository.save(Certification.create(
                round, member, fake.result(), fake.memo(), fake.spentAmount(), certifiedAt)));

        roundParticipantRepository.save(RoundParticipant.create(round, demoMember, true, true, 0));
        demoRoom.fakeMembers().values().forEach(member ->
                roundParticipantRepository.save(RoundParticipant.create(round, member, true, true, 0)));
        round.startVote(startedAt);
        voteRepository.save(Vote.create(round, demoMember, demoRoom.fakeMembers().get(DemoFakeMember.C)));
        demoRoom.fakeMembers().values().forEach(member -> voteRepository.save(Vote.create(round, member, demoMember)));
        round.announce(closedAt, RoundResultType.PICKED, demoMember);

        demoRoom.fakeMembers().forEach((fake, member) -> tapRepository.save(Tap.create(round, member, fake.tapCount())));
        demoMember.fillSeenResult(previous);
        demoRoom.fakeMembers().values().forEach(member -> member.fillSeenResult(previous));
        return demoRoom.room();
    }

    /** 두 방 모두 "자율 절약 방", 목표 300석, 함께할 인원 4명이고 시연 계정이 방장이다 (9번). */
    private DemoRoom createRoom(User user, Slot slot, Map<DemoFakeMember, User> fakes, LocalDateTime joinedAt,
                               LocalDate togetherDate) {
        Room room = roomRepository.save(Room.create(RoomGoal.ETC.title(), RoomGoal.ETC, MEMBER_LIMIT, POT_GOAL));
        RoomMember demoMember = roomMemberRepository.save(
                RoomMember.create(room, user, slot.nickname(), DEMO_ACCOUNT_COLOR, joinedAt));
        room.assignOwner(demoMember.getId());
        Map<DemoFakeMember, RoomMember> fakeMembers = new EnumMap<>(DemoFakeMember.class);
        fakes.forEach((fake, fakeUser) -> fakeMembers.put(fake, roomMemberRepository.save(
                RoomMember.create(room, fakeUser, fake.nickname(), fake.color(), joinedAt))));
        room.startTogether(togetherDate);
        return new DemoRoom(room, demoMember, fakeMembers);
    }

    private static LocalDateTime min(LocalDateTime first, LocalDateTime second) {
        return first.isBefore(second) ? first : second;
    }

    private record DemoRoom(Room room, RoomMember demoMember, Map<DemoFakeMember, RoomMember> fakeMembers) {
    }
}
