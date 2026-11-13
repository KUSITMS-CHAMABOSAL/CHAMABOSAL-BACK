package com.chamabosal.domain.vote.service;

import com.chamabosal.domain.vote.service.VoteStartService.DueRound;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDateTime;

/** 03:00에 전원 인증이 안 된 회차의 인증을 마감하고 투표를 시작한다 (VOTE-01 2번). */
@Slf4j
@Component
@RequiredArgsConstructor
public class VoteStartScheduler {

    private final VoteStartService voteStartService;
    private final Clock clock;

    /** 회차마다 트랜잭션을 따로 열어 한 방이 실패해도 다른 방은 계속 처리한다. */
    @Scheduled(cron = "0 0 3 * * *", zone = "Asia/Seoul")
    public void startDueVotes() {
        LocalDateTime now = LocalDateTime.now(clock);
        for (DueRound due : voteStartService.findDueRounds(now)) {
            try {
                voteStartService.startAtDeadline(due.roomId(), due.roundId(), now);
            } catch (RuntimeException e) {
                log.error("03:00 투표 시작 실패 roundId={}", due.roundId(), e);
            }
        }
    }
}
