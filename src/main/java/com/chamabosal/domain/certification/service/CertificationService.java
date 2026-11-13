package com.chamabosal.domain.certification.service;

import com.chamabosal.domain.certification.dto.request.CertificationCreateRequest;
import com.chamabosal.domain.certification.dto.response.CertificationCreateResponse;
import com.chamabosal.domain.certification.entity.Certification;
import com.chamabosal.domain.certification.entity.CertificationResult;
import com.chamabosal.domain.certification.repository.CertificationRepository;
import com.chamabosal.domain.room.entity.Room;
import com.chamabosal.domain.room.entity.RoomMember;
import com.chamabosal.domain.room.repository.RoomMemberRepository;
import com.chamabosal.domain.room.repository.RoomRepository;
import com.chamabosal.domain.round.entity.Round;
import com.chamabosal.domain.round.repository.RoundRepository;
import com.chamabosal.global.common.exception.BaseErrorCode;
import com.chamabosal.global.common.exception.BaseException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class CertificationService {

    private static final int MEMO_MAX_LENGTH = 40;
    private static final int SPENT_AMOUNT_MAX = 999_999;
    private static final String MEMO_MESSAGE = "한 줄 기록은 1~40자입니다.";
    private static final String SPENT_AMOUNT_MESSAGE = "못 참았으면 쓴 금액을 1~999,999원으로 입력해 주세요.";

    private final RoomRepository roomRepository;
    private final RoomMemberRepository roomMemberRepository;
    private final RoundRepository roundRepository;
    private final CertificationRepository certificationRepository;
    private final Clock clock;

    /** 방 행 락 안에서 확인하고 저장해서, 같은 방의 중복 인증과 회차 행 중복 생성을 막는다. 나가기(ROOM-07)와 같은 락이다. */
    @Transactional
    public CertificationCreateResponse create(Long userId, Long roomId, CertificationCreateRequest request) {
        validateMemo(request.memo());
        validateSpentAmount(request.result(), request.spentAmount());

        Room room = roomRepository.findWithLockById(roomId)
                .filter(found -> !found.isDeleted())
                .orElseThrow(() -> new BaseException(BaseErrorCode.ROOM_NOT_FOUND));
        RoomMember me = roomMemberRepository.findByRoom_IdAndUser_IdAndLeftAtIsNull(roomId, userId)
                .orElseThrow(() -> new BaseException(BaseErrorCode.NOT_ROOM_MEMBER));

        LocalDateTime now = LocalDateTime.now(clock);
        LocalDate roundDate = Round.dateOf(now);
        Optional<Round> found = roundRepository.findByRoom_IdAndRoundDate(roomId, roundDate);
        if (found.isPresent()
                && certificationRepository.existsByRound_IdAndMember_IdAndDeletedAtIsNull(found.get().getId(), me.getId())) {
            throw new BaseException(BaseErrorCode.ALREADY_CERTIFIED);
        }
        if (!isOpen(roomId, roundDate, found)) {
            throw new BaseException(BaseErrorCode.CERTIFICATION_NOT_OPEN);
        }

        Round round = found.orElseGet(() -> roundRepository.save(Round.create(room, roundDate)));
        Certification certification = certificationRepository.save(Certification.create(
                round, me, request.result(), request.memo(), request.spentAmount(), now));
        // 투표 시작(VOTE-01, #69): 이 등록으로 현재 멤버 전원이 인증했으면 같은 락 안에서 투표를 시작하고 true를 돌려준다.
        return CertificationCreateResponse.of(certification, false);
    }

    /**
     * 이전 회차 투표가 끝났거나 이전 회차가 없으면 열린다 (CERT-01 8번).
     * 그 회차가 이미 투표를 시작했으면 투표 시작 뒤 들어온 멤버라 다음 회차를 기다린다.
     */
    private boolean isOpen(Long roomId, LocalDate roundDate, Optional<Round> round) {
        if (round.isPresent() && !round.get().isCertifying()) {
            return false;
        }
        return roundRepository.findFirstByRoom_IdAndRoundDateBeforeOrderByRoundDateDesc(roomId, roundDate)
                .map(Round::isVoteFinished)
                .orElse(true);
    }

    /** 이모지도 한 글자로 세도록 코드포인트로 센다 (DB VARCHAR(40)과 같은 기준). 띄어쓰기만 있으면 비어 있는 것으로 본다. */
    private void validateMemo(String memo) {
        if (memo.isBlank() || memo.codePointCount(0, memo.length()) > MEMO_MAX_LENGTH) {
            throw BaseException.withField(BaseErrorCode.INVALID_REQUEST, MEMO_MESSAGE, "memo");
        }
    }

    /** ENDURED의 금액은 화면에서 보낼 일이 없어 확인하지 않고 저장하지도 않는다. */
    private void validateSpentAmount(CertificationResult result, Integer spentAmount) {
        if (result == CertificationResult.FAILED
                && (spentAmount == null || spentAmount < 1 || spentAmount > SPENT_AMOUNT_MAX)) {
            throw BaseException.withField(BaseErrorCode.INVALID_REQUEST, SPENT_AMOUNT_MESSAGE, "spentAmount");
        }
    }
}
