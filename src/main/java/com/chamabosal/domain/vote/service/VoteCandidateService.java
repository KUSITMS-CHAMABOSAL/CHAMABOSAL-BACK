package com.chamabosal.domain.vote.service;

import com.chamabosal.domain.certification.entity.Certification;
import com.chamabosal.domain.certification.photo.CertificationPhotoService;
import com.chamabosal.domain.certification.repository.CertificationRepository;
import com.chamabosal.domain.round.entity.RoundParticipant;
import com.chamabosal.domain.round.repository.RoundParticipantRepository;
import com.chamabosal.domain.store.service.StoreService;
import com.chamabosal.domain.vote.dto.response.VoteCandidateResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 투표 후보 계산 (VOTE-02). 그날 들어와서 인증하지 않은 멤버는 후보가 아니라 목록에서 뺀다.
 * 투표 중 나간 명단 멤버도 후보로 남는다 (VOTE-02 9번, Q-27).
 */
@Service
@RequiredArgsConstructor
public class VoteCandidateService {

    private final RoundParticipantRepository roundParticipantRepository;
    private final CertificationRepository certificationRepository;
    private final CertificationPhotoService certificationPhotoService;
    private final StoreService storeService;

    /** excludeMemberId가 null이면 아무도 빼지 않는다 (투표권 없음 상태의 내 화면은 나도 `인증 안 함`으로 보여준다, VOTE-05-10). */
    @Transactional(readOnly = true)
    public List<VoteCandidateResponse> findCandidates(Long roundId, Long excludeMemberId) {
        Map<Long, Certification> certificationsByMemberId = certificationRepository
                .findByRound_IdAndDeletedAtIsNull(roundId).stream()
                .collect(Collectors.toMap(c -> c.getMember().getId(), Function.identity()));
        List<RoundParticipant> candidates = roundParticipantRepository.findWithMemberByRound_Id(roundId).stream()
                .filter(RoundParticipant::isCandidate)
                .filter(participant -> excludeMemberId == null || !participant.getMember().getId().equals(excludeMemberId))
                .toList();
        Map<Long, List<String>> equipped = storeService.getEquippedItemCodesByMember(
                candidates.stream().map(RoundParticipant::getMember).toList());
        return candidates.stream()
                .map(participant -> {
                    Certification certification = certificationsByMemberId.get(participant.getMember().getId());
                    return VoteCandidateResponse.from(participant, certification,
                            certificationPhotoService.viewUrl(certification), equipped.get(participant.getMember().getId()));
                })
                .toList();
    }
}
