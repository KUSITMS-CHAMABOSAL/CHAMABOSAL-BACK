package com.chamabosal.domain.pot.service;

import com.chamabosal.domain.pot.dto.response.PotCelebrationResponse;
import com.chamabosal.domain.pot.entity.PotContribution;
import com.chamabosal.domain.pot.entity.PotReason;
import com.chamabosal.domain.pot.entity.RoomPot;
import com.chamabosal.domain.pot.repository.PotContributionRepository;
import com.chamabosal.domain.pot.repository.RoomPotRepository;
import com.chamabosal.domain.rice.entity.RiceReason;
import com.chamabosal.domain.rice.entity.RiceRefType;
import com.chamabosal.domain.rice.service.RiceService;
import com.chamabosal.domain.room.entity.Room;
import com.chamabosal.domain.room.entity.RoomMember;
import com.chamabosal.domain.room.repository.RoomMemberRepository;
import com.chamabosal.domain.room.repository.RoomRepository;
import com.chamabosal.global.common.exception.BaseErrorCode;
import com.chamabosal.global.common.exception.BaseException;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 공양미 한 솥 (POT-01). 적립·회수는 방 행 락을 잡은 트랜잭션 안에서 불러, 동시에 목표를 넘겨도 달성과 보상은 한 번이다.
 */
@Service
@RequiredArgsConstructor
public class PotService {

    private final RoomRepository roomRepository;
    private final RoomMemberRepository roomMemberRepository;
    private final RoomPotRepository roomPotRepository;
    private final PotContributionRepository potContributionRepository;
    private final RiceService riceService;
    private final Clock clock;

    /**
     * 개인 적립이 실제로 기록됐을 때만 같은 석을 솥에 쌓는다. 그래서 1명 방, 이미 받은 보상은 솥에도 쌓이지 않는다 (POT-01 1번).
     * 솥이 없으면 첫 솥을 만들고, 목표를 넘기면 그 순간 현재 멤버 전원에게 보상을 준다 (POT-01 4·5번). 쌓은 석, 쌓지 않았으면 0.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public int accrue(Room room, RoomMember member, PotReason reason, Long refId, int riceEarned) {
        if (riceEarned <= 0) {
            return 0;
        }
        RoomPot pot = roomPotRepository.findFirstByRoom_IdOrderBySeqDesc(room.getId())
                .orElseGet(() -> roomPotRepository.save(RoomPot.first(room)));
        pot.add(riceEarned);
        potContributionRepository.save(PotContribution.create(pot, member, riceEarned, reason, refId));
        if (!pot.isCompleted() && pot.isFilled()) {
            complete(room, pot);
        }
        return riceEarned;
    }

    /** 그 인증이 들어간 솥이 아직 진행 중이면 그만큼 뺀다. 이미 채운 솥이거나 이미 회수했으면 0 (RICE-02 2·3번). */
    @Transactional(propagation = Propagation.MANDATORY)
    public int recallCertification(RoomMember member, Long certificationId) {
        return potContributionRepository.findByMember_IdAndReasonAndRefId(
                        member.getId(), PotReason.CERTIFICATION, certificationId)
                .filter(paid -> !paid.getPot().isCompleted())
                .filter(paid -> !potContributionRepository.existsByMember_IdAndReasonAndRefId(
                        member.getId(), PotReason.RECALL, certificationId))
                .map(paid -> {
                    int recalled = paid.getPot().subtract(paid.getDelta());
                    potContributionRepository.save(
                            PotContribution.create(paid.getPot(), member, -recalled, PotReason.RECALL, certificationId));
                    return recalled;
                })
                .orElse(0);
    }

    /** 다 채운 솥의 축하 화면 (POT-01 14·15번). 같은 석이면 같은 순위이고, 그 안에서는 그 솥에 먼저 넣은 멤버가 앞이다. */
    @Transactional(readOnly = true)
    public PotCelebrationResponse getCelebration(Long userId, Long roomId, int seq) {
        roomRepository.findById(roomId)
                .filter(found -> !found.isDeleted())
                .orElseThrow(() -> new BaseException(BaseErrorCode.ROOM_NOT_FOUND));
        roomMemberRepository.findByRoom_IdAndUser_IdAndLeftAtIsNull(roomId, userId)
                .orElseThrow(() -> new BaseException(BaseErrorCode.NOT_ROOM_MEMBER));
        RoomPot pot = roomPotRepository.findByRoom_IdAndSeq(roomId, seq)
                .filter(RoomPot::isCompleted)
                .orElseThrow(() -> new BaseException(BaseErrorCode.POT_NOT_FOUND));

        Map<Long, RoomMember> members = new LinkedHashMap<>();
        Map<Long, Integer> amounts = new LinkedHashMap<>();
        for (PotContribution row : potContributionRepository.findWithMemberByPotId(pot.getId())) {
            members.putIfAbsent(row.getMember().getId(), row.getMember());
            amounts.merge(row.getMember().getId(), row.getDelta(), Integer::sum);
        }
        // 먼저 넣은 순서로 담겨 있어, 안정 정렬이면 같은 석 안에서 그 순서가 남는다.
        List<Long> ranked = members.values().stream()
                .filter(RoomMember::isCurrent)
                .map(RoomMember::getId)
                .sorted(Comparator.comparing(amounts::get, Comparator.reverseOrder()))
                .toList();

        List<PotCelebrationResponse.Contribution> contributions = new ArrayList<>();
        for (int i = 0; i < ranked.size(); i++) {
            RoomMember member = members.get(ranked.get(i));
            int amount = amounts.get(member.getId());
            int rank = i > 0 && amounts.get(ranked.get(i - 1)) == amount ? contributions.get(i - 1).rank() : i + 1;
            contributions.add(new PotCelebrationResponse.Contribution(
                    rank, member.getId(), member.getNickname(), member.getColor(), List.of(), amount));
        }
        return new PotCelebrationResponse(pot.getSeq(), pot.getGoalAmount(), pot.getRewardAmount(), contributions);
    }

    private void complete(Room room, RoomPot pot) {
        pot.complete(LocalDateTime.now(clock));
        room.completePot();
        roomMemberRepository.findByRoom_IdAndLeftAtIsNull(room.getId()).forEach(member -> riceService.accrue(
                room, member, RiceReason.POT, RiceRefType.ROOM_POT, pot.getId(), pot.getRewardAmount()));
    }
}
