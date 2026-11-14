package com.chamabosal.domain.certification.service;

import com.chamabosal.domain.certification.dto.request.CertificationCreateRequest;
import com.chamabosal.domain.certification.dto.request.CertificationUpdateRequest;
import com.chamabosal.domain.certification.dto.response.CertificationCreateResponse;
import com.chamabosal.domain.certification.dto.response.CertificationDeleteResponse;
import com.chamabosal.domain.certification.dto.response.CertificationUpdateResponse;
import com.chamabosal.domain.certification.entity.Certification;
import com.chamabosal.domain.certification.entity.CertificationLock;
import com.chamabosal.domain.certification.entity.CertificationResult;
import com.chamabosal.domain.certification.repository.CertificationRepository;
import com.chamabosal.domain.rice.entity.RiceReason;
import com.chamabosal.domain.rice.entity.RiceRefType;
import com.chamabosal.domain.rice.service.RiceService;
import com.chamabosal.domain.room.entity.Room;
import com.chamabosal.domain.room.entity.RoomMember;
import com.chamabosal.domain.room.repository.RoomMemberRepository;
import com.chamabosal.domain.room.repository.RoomRepository;
import com.chamabosal.domain.round.entity.Round;
import com.chamabosal.domain.round.entity.RoundStatus;
import com.chamabosal.domain.round.repository.RoundRepository;
import com.chamabosal.domain.vote.service.VoteStartService;
import com.chamabosal.global.common.exception.BaseErrorCode;
import com.chamabosal.global.common.exception.BaseException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
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
    private final VoteStartService voteStartService;
    private final RiceService riceService;
    private final Clock clock;

    /** 방 행 락 안에서 확인하고 저장해서, 같은 방의 중복 인증과 회차 행 중복 생성을 막는다. 나가기(ROOM-07)와 같은 락이다. */
    @Transactional
    public CertificationCreateResponse create(Long userId, Long roomId, CertificationCreateRequest request) {
        // 회차는 요청을 받은 시각으로 정한다. 락을 기다리다 03:00을 넘겨도 회차가 바뀌지 않게 락보다 먼저 읽는다.
        LocalDateTime now = LocalDateTime.now(clock);
        validateMemo(request.memo());
        validateSpentAmount(request.result(), request.spentAmount());

        Room room = roomRepository.findWithLockById(roomId)
                .filter(found -> !found.isDeleted())
                .orElseThrow(() -> new BaseException(BaseErrorCode.ROOM_NOT_FOUND));
        RoomMember me = roomMemberRepository.findByRoom_IdAndUser_IdAndLeftAtIsNull(roomId, userId)
                .orElseThrow(() -> new BaseException(BaseErrorCode.NOT_ROOM_MEMBER));

        LocalDate roundDate = Round.dateOf(now);
        Optional<Round> found = roundRepository.findByRoom_IdAndRoundDate(roomId, roundDate);
        if (found.isPresent()
                && certificationRepository.existsByRound_IdAndMember_IdAndDeletedAtIsNull(found.get().getId(), me.getId())) {
            throw new BaseException(BaseErrorCode.ALREADY_CERTIFIED);
        }
        if (!isOpen(roomId, roundDate, found, me.getId())) {
            throw new BaseException(BaseErrorCode.CERTIFICATION_NOT_OPEN);
        }

        Round round = found.orElseGet(() -> roundRepository.save(Round.create(room, roundDate)));
        Certification certification = certificationRepository.save(Certification.create(
                round, me, request.result(), request.memo(), request.spentAmount(), now));
        int riceEarned = riceService.accrue(room, me, RiceReason.CERTIFICATION, RiceRefType.CERTIFICATION,
                certification.getId(), RiceService.CERTIFICATION_REWARD);
        // 마지막 인증이면 같은 락 안에서 투표를 시작한다 (CERT-01 9번).
        boolean isVoteStarted = voteStartService.startIfAllCertified(round, now);
        return CertificationCreateResponse.from(certification, riceEarned, isVoteStarted);
    }

    /**
     * 수정은 등록과 같은 입력 기준으로 통째로 바꾸고 공양미를 다시 주지 않는다 (CERT-02).
     * 인증 ID로 방 ID만 먼저 읽고 방 행 락을 잡는다. REPEATABLE READ면 락 전 조회가 스냅샷을 고정해
     * 락 뒤에도 투표 시작 전 상태를 보므로 READ COMMITTED로 읽는다 (VoteSubmitService와 같은 이유).
     */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public CertificationUpdateResponse update(Long userId, Long certificationId, CertificationUpdateRequest request) {
        validateMemo(request.memo());
        validateSpentAmount(request.result(), request.spentAmount());

        Certification certification = findWithLock(certificationId);
        if (certification.isDeleted()) {
            throw new BaseException(BaseErrorCode.CERTIFICATION_NOT_FOUND);
        }
        checkMine(userId, certification);
        checkNotLocked(certification, LocalDateTime.now(clock));
        certification.update(request.result(), request.memo(), request.spentAmount());
        return CertificationUpdateResponse.from(certification);
    }

    /** 이미 삭제한 내 인증이면 잠금과 상관없이 성공이다 (CERT-02 12번). */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public CertificationDeleteResponse delete(Long userId, Long certificationId) {
        Certification certification = findWithLock(certificationId);
        checkMine(userId, certification);
        if (certification.isDeleted()) {
            return new CertificationDeleteResponse(0);
        }
        LocalDateTime now = LocalDateTime.now(clock);
        checkNotLocked(certification, now);
        certification.delete(now);
        return new CertificationDeleteResponse(0);
    }

    /** 방 행 락을 잡은 뒤에 인증을 읽는다. 없는 인증이면 CERT_002. */
    private Certification findWithLock(Long certificationId) {
        Long roomId = certificationRepository.findRoomIdByCertificationId(certificationId)
                .orElseThrow(() -> new BaseException(BaseErrorCode.CERTIFICATION_NOT_FOUND));
        roomRepository.findWithLockById(roomId);
        return certificationRepository.findById(certificationId)
                .orElseThrow(() -> new BaseException(BaseErrorCode.CERTIFICATION_NOT_FOUND));
    }

    /** 방에서 나갔으면 그 방의 인증은 본인 인증으로 보지 않는다. 다시 들어오면 같은 멤버 행이라 다시 본인이다. */
    private void checkMine(Long userId, Certification certification) {
        Long roomId = certification.getRound().getRoom().getId();
        boolean isMine = roomMemberRepository.findByRoom_IdAndUser_IdAndLeftAtIsNull(roomId, userId)
                .map(me -> me.getId().equals(certification.getMember().getId()))
                .orElse(false);
        if (!isMine) {
            throw new BaseException(BaseErrorCode.NOT_MY_CERTIFICATION);
        }
    }

    private void checkNotLocked(Certification certification, LocalDateTime now) {
        CertificationLock lock = certification.lockAt(now);
        if (lock == CertificationLock.EDIT_TIME_OVER) {
            throw new BaseException(BaseErrorCode.CERTIFICATION_EDIT_TIME_OVER);
        }
        if (lock == CertificationLock.VOTE_STARTED) {
            throw new BaseException(BaseErrorCode.CERTIFICATION_VOTE_STARTED);
        }
    }

    /**
     * 이전 회차 투표가 끝났거나 이전 회차가 없으면 열린다 (CERT-01 8번).
     * 그 회차가 이미 투표를 시작했으면 투표 시작 뒤 들어온 멤버라 다음 회차를 기다린다.
     * 투표가 열리지 않은 회차는 그 회차에서 인증을 삭제한 멤버만 다시 인증할 수 있다 (CERT-01 10번).
     */
    private boolean isOpen(Long roomId, LocalDate roundDate, Optional<Round> round, Long memberId) {
        if (round.isPresent() && round.get().getStatus() == RoundStatus.NO_VOTE) {
            return certificationRepository.existsByRound_IdAndMember_IdAndDeletedAtIsNotNull(round.get().getId(), memberId);
        }
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
