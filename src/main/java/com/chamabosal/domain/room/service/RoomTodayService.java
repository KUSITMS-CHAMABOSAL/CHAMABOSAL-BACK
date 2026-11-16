package com.chamabosal.domain.room.service;

import com.chamabosal.domain.certification.entity.Certification;
import com.chamabosal.domain.certification.entity.CertificationLock;
import com.chamabosal.domain.certification.entity.CertificationResult;
import com.chamabosal.domain.certification.photo.CertificationPhotoService;
import com.chamabosal.domain.certification.repository.CertificationRepository;
import com.chamabosal.domain.certification.service.CertificationService;
import com.chamabosal.domain.nabal.entity.NabalLoss;
import com.chamabosal.domain.nabal.repository.NabalLossRepository;
import com.chamabosal.domain.pot.repository.RoomPotRepository;
import com.chamabosal.domain.room.dto.response.RoomTodayResponse;
import com.chamabosal.domain.room.dto.response.RoomTodayResponse.CertificationInfo;
import com.chamabosal.domain.room.dto.response.RoomTodayResponse.Entry;
import com.chamabosal.domain.room.dto.response.RoomTodayResponse.Me;
import com.chamabosal.domain.room.dto.response.RoomTodayResponse.Member;
import com.chamabosal.domain.room.dto.response.RoomTodayResponse.Pot;
import com.chamabosal.domain.room.dto.response.RoomTodayResponse.RoundSummary;
import com.chamabosal.domain.room.entity.Room;
import com.chamabosal.domain.room.entity.RoomMember;
import com.chamabosal.domain.room.repository.RoomMemberRepository;
import com.chamabosal.domain.room.repository.RoomRepository;
import com.chamabosal.domain.round.entity.Round;
import com.chamabosal.domain.round.entity.RoundResultType;
import com.chamabosal.domain.round.entity.RoundStatus;
import com.chamabosal.domain.round.repository.RoundRepository;
import com.chamabosal.domain.tap.repository.TapRepository;
import com.chamabosal.domain.vote.service.VoteStartService;
import com.chamabosal.global.common.exception.BaseErrorCode;
import com.chamabosal.global.common.exception.BaseException;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 오늘 탭 (ROOM-02). 지금 회차 날짜(03:00 전이면 전날)의 회차를 기준으로 방·회차·멤버·진입 항목을 한 번에 돌려준다.
 * 멤버별 집계(이번 달 참은·지목 수, 나발)는 멤버마다 조회하지 않고 방 단위로 한 번씩 모아 읽는다.
 */
@Service
@RequiredArgsConstructor
public class RoomTodayService {

    private final RoomRepository roomRepository;
    private final RoomMemberRepository roomMemberRepository;
    private final RoundRepository roundRepository;
    private final CertificationRepository certificationRepository;
    private final CertificationService certificationService;
    private final CertificationPhotoService certificationPhotoService;
    private final TapRepository tapRepository;
    private final RoomPotRepository roomPotRepository;
    private final NabalLossRepository nabalLossRepository;
    private final Clock clock;

    @Transactional(readOnly = true)
    public RoomTodayResponse getToday(Long userId, Long roomId) {
        Room room = roomRepository.findById(roomId)
                .filter(found -> !found.isDeleted())
                .orElseThrow(() -> new BaseException(BaseErrorCode.ROOM_NOT_FOUND));
        RoomMember me = roomMemberRepository.findByRoom_IdAndUser_IdAndLeftAtIsNull(room.getId(), userId)
                .orElseThrow(() -> new BaseException(BaseErrorCode.NOT_ROOM_MEMBER));
        LocalDateTime now = LocalDateTime.now(clock);
        LocalDate roundDate = Round.dateOf(now);

        List<RoomMember> members = roomMemberRepository.findByRoom_IdAndLeftAtIsNull(room.getId()).stream()
                .sorted(Comparator.comparing(RoomMember::getJoinedAt).thenComparing(RoomMember::getId))
                .toList();
        Optional<Round> round = roundRepository.findByRoom_IdAndRoundDate(room.getId(), roundDate);
        Map<Long, Certification> certifications = round
                .map(found -> certificationRepository.findByRound_IdAndDeletedAtIsNull(found.getId()).stream()
                        .collect(Collectors.toMap(c -> c.getMember().getId(), c -> c)))
                .orElse(Map.of());
        int certifiedCount = (int) members.stream().filter(member -> certifications.containsKey(member.getId())).count();
        boolean isAllCertified = certifiedCount == members.size();
        // 인증 등록과 같은 기준을 써서 버튼과 실제 등록 결과가 어긋나지 않게 한다 (14번, 코드리뷰 반영).
        boolean isOpen = certificationService.isOpen(room.getId(), roundDate, round, me.getId());

        RoundSummary roundSummary = new RoundSummary(round.map(Round::getId).orElse(null),
                round.map(Round::getStatus).orElse(RoundStatus.CERTIFYING), certifiedCount, isAllCertified, isOpen);
        // 확인 시트가 "등록하면 바로 투표가 시작된다"고 안내해서, 투표가 시작되는 경우만 표시한다 (10번, WF 55).
        // 3명 미만이거나 이미 투표 없음이 된 회차는 전원이 인증해도 투표가 열리지 않는다 (VOTE-01 4번).
        boolean startsVote = members.size() >= VoteStartService.MIN_VOTERS && round.map(Round::isCertifying).orElse(true);
        Me meInfo = new Me(isOpen && startsVote && !certifications.containsKey(me.getId())
                && certifiedCount == members.size() - 1, 0);
        return new RoomTodayResponse(roomInfo(room, me, members.size(), roundDate), pot(room),
                roundSummary, members(room, members, me, certifications, roundDate, now), meInfo, entry(room, me, now));
    }

    private RoomTodayResponse.Room roomInfo(Room room, RoomMember me, int memberCount, LocalDate roundDate) {
        boolean hasVotingRound = roundRepository.existsByRoom_IdAndStatus(room.getId(), RoundStatus.VOTING);
        int daysTogether = (int) ChronoUnit.DAYS.between(Round.dateOf(room.getCreatedAt()), roundDate);
        return new RoomTodayResponse.Room(room.getId(), room.getName(), room.getGoal(), memberCount, room.getMemberLimit(),
                me.getId().equals(room.getOwnerMemberId()), room.getPotCompletedCount(), hasVotingRound, daysTogether);
    }

    private List<Member> members(Room room, List<RoomMember> members, RoomMember me, Map<Long, Certification> certifications,
                                 LocalDate roundDate, LocalDateTime now) {
        YearMonth month = YearMonth.from(roundDate);
        Map<Long, Long> endured = toCounts(certificationRepository.countByMemberInPeriod(
                room.getId(), CertificationResult.ENDURED, month.atDay(1), month.atEndOfMonth()));
        Map<Long, Long> picked = toCounts(roundRepository.countPickedByMemberInPeriod(
                room.getId(), RoundResultType.PICKED, month.atDay(1), month.atEndOfMonth()));
        Map<Long, List<Integer>> nabals = nabalLossRepository.findByUser_IdInAndReplantedAtIsNull(
                        members.stream().map(member -> member.getUser().getId()).toList()).stream()
                .collect(Collectors.groupingBy(loss -> loss.getUser().getId(),
                        Collectors.mapping(NabalLoss::getNabalNumber, Collectors.toList())));
        return members.stream()
                .map(member -> new Member(member.getId(), member.getNickname(), member.getColor(), List.of(),
                        nabals.getOrDefault(member.getUser().getId(), List.of()).stream().sorted().toList(),
                        member.getId().equals(me.getId()),
                        endured.getOrDefault(member.getId(), 0L).intValue(),
                        picked.getOrDefault(member.getId(), 0L).intValue(),
                        certificationInfo(certifications.get(member.getId()), now)))
                .toList();
    }

    private CertificationInfo certificationInfo(Certification certification, LocalDateTime now) {
        if (certification == null) {
            return null;
        }
        CertificationLock lock = certification.lockAt(now);
        String lockReason = lock == null ? null : switch (lock) {
            case EDIT_TIME_OVER -> "TIME_OVER";
            case VOTE_STARTED -> "VOTE_STARTED";
        };
        return new CertificationInfo(certification.getId(), certification.getResult(), certification.getMemo(),
                certification.getSpentAmount(), certificationPhotoService.viewUrl(certification),
                certification.getCertifiedAt(), null,
                certification.getEditableUntil(), lockReason);
    }

    /** 지금 쌓고 있는 솥. 첫 적립 전에는 솥 행이 없어 0석과 방 목표 석으로 돌려준다 (POT-01). */
    private Pot pot(Room room) {
        return roomPotRepository.findFirstByRoom_IdOrderBySeqDesc(room.getId())
                .map(pot -> new Pot(pot.getAmount(), pot.getGoalAmount()))
                .orElseGet(() -> new Pot(0, room.getPotGoalAmount()));
    }

    /** 톡톡 → 결과 → 한 솥 축하 순서로 클라이언트가 재생한다 (13번). */
    private Entry entry(Room room, RoomMember me, LocalDateTime now) {
        Long unplayedTapRoundId = tapRepository.findDeliveredUnplayedRoundIds(room.getId(), me.getId(),
                        RoundResultType.PICKED, Round.tapDeliveredVoteClosedBy(now))
                .stream().findFirst().map(row -> (Long) row[0]).orElse(null);
        Long unseenResultRoundId = roundRepository
                .findFirstByRoom_IdAndStatusOrderByRoundDateDesc(room.getId(), RoundStatus.ANNOUNCED)
                .filter(latest -> me.hasUnseenResult(latest.getRoundDate()))
                .map(Round::getId)
                .orElse(null);
        return new Entry(unplayedTapRoundId, unseenResultRoundId, me.hasUnseenPot());
    }

    private static Map<Long, Long> toCounts(List<Object[]> rows) {
        return rows.stream().collect(Collectors.toMap(row -> (Long) row[0], row -> (Long) row[1]));
    }
}
