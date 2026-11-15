package com.chamabosal.domain.nabal.service;

import com.chamabosal.domain.certification.repository.CertificationRepository;
import com.chamabosal.domain.nabal.entity.NabalLoss;
import com.chamabosal.domain.nabal.repository.NabalLossRepository;
import com.chamabosal.domain.room.entity.RoomMember;
import com.chamabosal.domain.room.repository.RoomMemberRepository;
import com.chamabosal.domain.room.repository.RoomRepository;
import com.chamabosal.domain.round.entity.Round;
import com.chamabosal.domain.user.entity.User;
import com.chamabosal.domain.user.repository.UserRepository;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.OptionalInt;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

/** 회차 인증 마감 뒤 인증을 빠뜨린 방마다 나발을 하나씩 뺀다 (NABAL-01). */
@Service
@RequiredArgsConstructor
public class NabalLossService {

    private final UserRepository userRepository;
    private final RoomRepository roomRepository;
    private final RoomMemberRepository roomMemberRepository;
    private final CertificationRepository certificationRepository;
    private final NabalLossRepository nabalLossRepository;
    private final NabalPicker nabalPicker;

    /** 판정 대상: 삭제하지 않았고 살아 있는 방에 참여 중인 사용자 (7번). 트랜잭션 밖에서 쓰도록 ID만 돌려준다. */
    @Transactional(readOnly = true)
    public List<Long> findTargetUserIds() {
        return roomMemberRepository.findActiveUserIds();
    }

    /**
     * 사용자 하나를 판정한다. 나발 심기와 같은 사용자 행 락을 잡아 빠진 번호 목록을 바꾸는 일이 겹치지 않게 한다.
     * 그 회차 날짜 전에 들어온 현재 방마다 삭제하지 않은 내 인증이 없으면 남은 번호 중 하나를 뺀다 (4·5·6·8번).
     * 같은 방·회차 날짜에 이미 기록이 있으면 넘어가서 다시 돌아도 한 번이다 (9번).
     * 02:59에 시작한 인증이 판정 뒤에 커밋되면 인증했는데도 빠지므로, 인증 등록과 같은 방 행 락을 잡고 READ COMMITTED로
     * 다시 읽어 커밋된 인증을 본다. 락 순서는 사용자 → 방으로 나가기·입장과 같다 (코드리뷰 반영).
     */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void judge(Long userId, LocalDate roundDate) {
        User user = userRepository.findWithLockById(userId)
                .filter(found -> !found.isDeleted())
                .orElse(null);
        if (user == null) {
            return;
        }
        Set<Integer> fallen = new HashSet<>(nabalLossRepository.findByUser_IdAndReplantedAtIsNullOrderByNabalNumber(userId)
                .stream().map(NabalLoss::getNabalNumber).toList());
        // 내 데이터 삭제(USER-05)와 같은 방 ID 순서로 먼저 모두 잠가, 두 사용자가 방을 반대 순서로 잡아 교착하지 않게 한다 (코드리뷰 반영).
        roomMemberRepository.findCurrentWithRoom(userId).stream().map(member -> member.getRoom().getId()).sorted()
                .forEach(roomRepository::findWithLockById);
        // 락 전에 읽은 사이 방장이 내보냈을 수 있어 다시 읽는다. 입장은 사용자 행 락에 막혀 방이 늘지 않는다 (코드리뷰 반영).
        List<RoomMember> members = roomMemberRepository.findCurrentWithRoom(userId);
        for (RoomMember member : members) {
            if (!isUncertified(member, roundDate)
                    || nabalLossRepository.existsByUser_IdAndRoom_IdAndRoundDate(userId, member.getRoom().getId(), roundDate)) {
                continue;
            }
            OptionalInt picked = nabalPicker.pick(fallen);
            if (picked.isEmpty()) {
                return;
            }
            fallen.add(picked.getAsInt());
            nabalLossRepository.save(NabalLoss.create(user, member.getRoom(), roundDate, picked.getAsInt()));
        }
    }

    /** 그 회차 날짜에 들어온 방은 세지 않는다 (CERT-05 2번과 같은 기준). 회차 행이 없는 방도 미인증이다. */
    private boolean isUncertified(RoomMember member, LocalDate roundDate) {
        if (!Round.dateOf(member.getJoinedAt()).isBefore(roundDate)) {
            return false;
        }
        return !certificationRepository.existsByRound_Room_IdAndRound_RoundDateAndMember_IdAndDeletedAtIsNull(
                member.getRoom().getId(), roundDate, member.getId());
    }
}
