package com.chamabosal.domain.certification.service;

import com.chamabosal.domain.certification.photo.CertificationPhotoService;
import com.chamabosal.domain.certification.repository.CertificationRepository;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 나간 멤버의 인증에서 남은 사진을 지우고 한 줄과 쓴 금액을 비운다 (CLEAN-01). 6개월 배치와 방 닫기(ROOM-09 6번)가 같이 쓴다.
 * 첫 일괄 update가 실행 전에 flush하고 끝나면 영속성 컨텍스트를 비우므로, 부른 뒤에는 앞서 읽은 엔티티를 쓰지 않는다. 나간 멤버만 정리한다.
 */
@Component
@RequiredArgsConstructor
public class LeftMemberRecordCleaner {

    private final CertificationRepository certificationRepository;
    private final CertificationPhotoService certificationPhotoService;

    /** 정리한 인증 수와 지운 사진 수 */
    public record Result(int cleared, int photos) {
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public Result clear(Collection<Long> memberIds, LocalDateTime now) {
        if (memberIds.isEmpty()) {
            return new Result(0, 0);
        }
        int cleared = certificationRepository.clearRecordsOfMembers(memberIds, now);
        List<String> photoKeys = certificationRepository.findImageKeysOfMembers(memberIds);
        int photos = photoKeys.isEmpty() ? 0 : certificationRepository.markImagesDeleted(photoKeys, now);
        photoKeys.forEach(certificationPhotoService::deleteAfterCommit);
        return new Result(cleared, photos);
    }
}
