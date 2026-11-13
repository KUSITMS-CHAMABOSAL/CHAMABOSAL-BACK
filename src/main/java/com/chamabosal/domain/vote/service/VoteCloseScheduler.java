package com.chamabosal.domain.vote.service;

import com.chamabosal.domain.vote.service.VoteCloseService.DueRound;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDateTime;

/** 15:00에 전원 투표가 안 된 회차를 마감하고 결과를 판정한다 (VOTE-04 2번). */
@Slf4j
@Component
@RequiredArgsConstructor
public class VoteCloseScheduler {

    private final VoteCloseService voteCloseService;
    private final Clock clock;

    /** 회차마다 트랜잭션을 따로 열어 한 방이 실패해도 다른 방은 계속 처리한다. */
    @Scheduled(cron = "0 0 15 * * *", zone = "Asia/Seoul")
    public void closeDueVotes() {
        LocalDateTime now = LocalDateTime.now(clock);
        for (DueRound due : voteCloseService.findDueRounds(now)) {
            try {
                voteCloseService.closeAtDeadline(due.roomId(), due.roundId(), now);
            } catch (RuntimeException e) {
                log.error("15:00 투표 마감 실패 roundId={}", due.roundId(), e);
            }
        }
    }
}
