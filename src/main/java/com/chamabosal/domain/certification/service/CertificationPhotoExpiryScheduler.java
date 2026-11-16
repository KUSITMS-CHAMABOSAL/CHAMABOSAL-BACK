package com.chamabosal.domain.certification.service;

import com.chamabosal.domain.certification.photo.CertificationPhotoService;
import com.chamabosal.domain.certification.repository.CertificationRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 회차가 끝나고 7일이 지난 인증 사진을 지운다 (CERT-04 5번). 매시 돌아서 기한 뒤 늦어도 한 시간 안에 지운다.
 * 지운 것으로 먼저 표시하고 저장소 삭제는 커밋 뒤에 해서, 다시 돌아도 같은 사진을 다시 고르지 않는다 (CERT-04-5).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CertificationPhotoExpiryScheduler {

    static final Duration RETENTION = Duration.ofDays(7);

    private final CertificationRepository certificationRepository;
    private final CertificationPhotoService certificationPhotoService;
    private final Clock clock;

    // shortcut: 대상 키를 한 번에 읽는다. 한 시간에 지울 사진이 수천 장을 넘으면 페이지로 나눈다.
    @Scheduled(cron = "0 20 * * * *", zone = "Asia/Seoul")
    @Transactional
    public void deleteExpired() {
        LocalDateTime now = LocalDateTime.now(clock);
        List<String> keys = certificationRepository.findExpiredImageKeys(now.minus(RETENTION));
        if (keys.isEmpty()) {
            return;
        }
        int marked = certificationRepository.markImagesDeleted(keys, now);
        keys.forEach(certificationPhotoService::deleteAfterCommit);
        log.info("기한이 지난 인증 사진 {}장 삭제", marked);
    }
}
