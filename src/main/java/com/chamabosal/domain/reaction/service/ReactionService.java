package com.chamabosal.domain.reaction.service;

import com.chamabosal.domain.certification.entity.Certification;
import com.chamabosal.domain.certification.repository.CertificationRepository;
import com.chamabosal.domain.pot.entity.PotReason;
import com.chamabosal.domain.pot.service.PotService;
import com.chamabosal.domain.reaction.dto.response.ReactionPutResponse;
import com.chamabosal.domain.reaction.entity.Reaction;
import com.chamabosal.domain.reaction.entity.ReactionType;
import com.chamabosal.domain.reaction.repository.ReactionRepository;
import com.chamabosal.domain.rice.entity.RiceReason;
import com.chamabosal.domain.rice.entity.RiceRefType;
import com.chamabosal.domain.rice.service.RiceService;
import com.chamabosal.domain.room.entity.Room;
import com.chamabosal.domain.room.entity.RoomMember;
import com.chamabosal.domain.room.repository.RoomMemberRepository;
import com.chamabosal.domain.room.repository.RoomRepository;
import com.chamabosal.domain.round.entity.RoundStatus;
import com.chamabosal.global.common.exception.BaseErrorCode;
import com.chamabosal.global.common.exception.BaseException;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 인증 리액션 (REACT-01). 투표 시작과 엇갈리지 않게 인증이 있는 방의 행 락을 잡는다.
 * 인증 ID로 방 ID만 먼저 읽고 락을 잡아서, 락 뒤의 조회가 커밋된 값을 보도록 READ COMMITTED로 읽는다 (CertificationService와 같은 이유).
 */
@Service
@RequiredArgsConstructor
public class ReactionService {

    private final CertificationRepository certificationRepository;
    private final ReactionRepository reactionRepository;
    private final RoomRepository roomRepository;
    private final RoomMemberRepository roomMemberRepository;
    private final RiceService riceService;
    private final PotService potService;

    /** 확인 순서: 없는 인증 → 방 멤버 아님 → 본인 인증 → 투표 시작 후 (REACT-01 9번). 처음 남길 때만 보상한다 (7번). */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public ReactionPutResponse put(Long userId, Long certificationId, ReactionType type) {
        Room room = lockRoom(certificationId);
        Certification certification = findLive(certificationId);
        RoomMember me = findMe(room, userId);
        checkNotMine(certification, me);
        checkOpen(certification);

        reactionRepository.findByCertification_IdAndMember_Id(certificationId, me.getId()).ifPresentOrElse(
                reaction -> reaction.change(type),
                () -> reactionRepository.save(Reaction.create(certification, me, type)));
        // 장부 UNIQUE로 인증 1건당 한 번만 기록돼, 바꾸거나 취소한 뒤 다시 남겨도 0이다 (Q-90).
        int riceEarned = riceService.accrue(room, me, RiceReason.REACTION, RiceRefType.CERTIFICATION, certificationId,
                RiceService.REACTION_REWARD);
        int potEarned = potService.accrue(room, me, PotReason.REACTION, certificationId, riceEarned);
        return new ReactionPutResponse(riceEarned, potEarned);
    }

    /** 확인 순서: 없는 인증 → 방 멤버 아님 → 남긴 리액션 없음(성공) → 본인 인증 → 투표 시작 후 (REACT-01 10번). 보상은 회수하지 않는다. */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void cancel(Long userId, Long certificationId) {
        Room room = lockRoom(certificationId);
        Certification certification = findLive(certificationId);
        RoomMember me = findMe(room, userId);
        Optional<Reaction> mine = reactionRepository.findByCertification_IdAndMember_Id(certificationId, me.getId());
        if (mine.isEmpty()) {
            return;
        }
        checkNotMine(certification, me);
        checkOpen(certification);
        reactionRepository.delete(mine.get());
    }

    private Room lockRoom(Long certificationId) {
        Long roomId = certificationRepository.findRoomIdByCertificationId(certificationId)
                .orElseThrow(() -> new BaseException(BaseErrorCode.CERTIFICATION_NOT_FOUND));
        return roomRepository.findWithLockById(roomId)
                .orElseThrow(() -> new BaseException(BaseErrorCode.CERTIFICATION_NOT_FOUND));
    }

    private Certification findLive(Long certificationId) {
        return certificationRepository.findById(certificationId)
                .filter(found -> !found.isDeleted())
                .orElseThrow(() -> new BaseException(BaseErrorCode.CERTIFICATION_NOT_FOUND));
    }

    private RoomMember findMe(Room room, Long userId) {
        return roomMemberRepository.findByRoom_IdAndUser_IdAndLeftAtIsNull(room.getId(), userId)
                .orElseThrow(() -> new BaseException(BaseErrorCode.NOT_ROOM_MEMBER));
    }

    private static void checkNotMine(Certification certification, RoomMember me) {
        if (certification.getMember().getId().equals(me.getId())) {
            throw new BaseException(BaseErrorCode.REACTION_ON_MINE);
        }
    }

    /** 인증이 속한 회차가 인증 중인 동안만 바꿀 수 있다. 투표 없음 회차도 확정되면 마감한다 (REACT-01 5번, Q-33 임시 결정). */
    private static void checkOpen(Certification certification) {
        if (certification.getRound().getStatus() != RoundStatus.CERTIFYING) {
            throw new BaseException(BaseErrorCode.REACTION_CLOSED);
        }
    }
}
