package com.chamabosal.domain.room.service;

import com.chamabosal.domain.room.repository.KickBlockRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 보관 기간(삭제 + 6개월)이 지난 내보내기 차단 해시를 지운다 (CLEAN-02). 입장 판단은 만료 시각을 직접 비교해서
 * 배치가 늦거나 실패해도 만료된 기록으로 입장을 막지 않는다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class KickBlockCleanupScheduler {

    private final KickBlockRepository kickBlockRepository;
    private final Clock clock;

    @Scheduled(cron = "0 0 4 * * *", zone = "Asia/Seoul")
    @Transactional
    public void deleteExpired() {
        int deleted = kickBlockRepository.deleteExpired(LocalDateTime.now(clock));
        if (deleted > 0) {
            log.info("만료된 내보내기 차단 기록 {}건 삭제", deleted);
        }
    }
}
