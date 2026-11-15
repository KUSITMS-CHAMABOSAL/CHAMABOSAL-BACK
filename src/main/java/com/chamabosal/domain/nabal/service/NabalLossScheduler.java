package com.chamabosal.domain.nabal.service;

import com.chamabosal.domain.round.entity.Round;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 03:00에 인증이 마감된 바로 전 회차 날짜를 판정한다 (NABAL-01 3번). 사용자마다 트랜잭션을 따로 열어 한 명이 실패해도 계속한다.
 * 서버가 이 시각에 내려가 있었던 날은 몰아서 판정하지 않는다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class NabalLossScheduler {

    private final NabalLossService nabalLossService;
    private final Clock clock;

    @Scheduled(cron = "0 0 3 * * *", zone = "Asia/Seoul")
    public void judgeClosedRound() {
        LocalDate roundDate = Round.dateOf(LocalDateTime.now(clock)).minusDays(1);
        for (Long userId : nabalLossService.findTargetUserIds()) {
            try {
                nabalLossService.judge(userId, roundDate);
            } catch (RuntimeException e) {
                log.error("03:00 나발 판정 실패 userId={}", userId, e);
            }
        }
    }
}
