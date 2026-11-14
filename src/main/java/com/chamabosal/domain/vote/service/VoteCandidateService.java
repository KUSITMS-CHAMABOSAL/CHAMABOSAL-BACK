package com.chamabosal.domain.vote.service;

import com.chamabosal.domain.certification.entity.Certification;
import com.chamabosal.domain.certification.repository.CertificationRepository;
import com.chamabosal.domain.round.repository.RoundParticipantRepository;
import com.chamabosal.domain.vote.dto.response.VoteCandidateResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 투표 후보 계산 (VOTE-02). `RoundParticipant.isCandidate`는 "선택 가능한가"를 뜻해서 휴식 중이면 항상 false인데
 * (VOTE-01·CERT-05 스냅샷), 휴식 중인 멤버는 목록에서 빼는 게 아니라 `휴식 중`으로 보여주되 선택만 막아야 한다 (VOTE-02 3번).
 * 그래서 "오늘 들어와서 미인증"(`isCandidate`, `isResting` 모두 false, 목록에서도 제외)과
 * "휴식 중"(`isCandidate` false, `isResting` true, 목록에는 넣고 선택만 막음)을 `isResting`으로 구분한다.
 * 투표 중 나간 명단 멤버를 후보에서 뺄지는 아직 정하지 않아 (Q-27) 이번에는 빼지 않는다.
 */
@Service
@RequiredArgsConstructor
public class VoteCandidateService {

    private final RoundParticipantRepository roundParticipantRepository;
    private final CertificationRepository certificationRepository;

    /** excludeMemberId가 null이면 아무도 빼지 않는다 (투표권 없음 상태의 내 화면은 나도 `인증 안 함`으로 보여준다, VOTE-05-10). */
    @Transactional(readOnly = true)
    public List<VoteCandidateResponse> findCandidates(Long roundId, Long excludeMemberId) {
        Map<Long, Certification> certificationsByMemberId = certificationRepository
                .findByRound_IdAndDeletedAtIsNull(roundId).stream()
                .collect(Collectors.toMap(c -> c.getMember().getId(), Function.identity()));
        return roundParticipantRepository.findWithMemberByRound_Id(roundId).stream()
                .filter(participant -> participant.isCandidate() || participant.isResting())
                .filter(participant -> excludeMemberId == null || !participant.getMember().getId().equals(excludeMemberId))
                .map(participant -> VoteCandidateResponse.from(
                        participant, certificationsByMemberId.get(participant.getMember().getId())))
                .toList();
    }
}
