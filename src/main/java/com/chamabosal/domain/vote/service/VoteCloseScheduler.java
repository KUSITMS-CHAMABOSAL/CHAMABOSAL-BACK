package com.chamabosal.domain.vote.service;

import com.chamabosal.domain.vote.service.VoteCloseService.DueRound;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDateTime;

/**
 * 15:00까지 전원 투표가 안 된 회차를 마감하고 결과를 판정한다 (VOTE-04 2번).
 * 하루 한 번만 돌면 그 실행에서 실패한 회차(락 타임아웃 등 일시적 오류)가 다음 날 15:00까지 `VOTING`으로 남아
 * 다음 회차 인증을 계속 막는다. 그래서 5분마다 돌며 마감 시각이 지난 회차를 다시 찾는다 (코드리뷰 반영).
 * 이미 마감된 회차는 `VoteCloseService`가 상태를 다시 확인해 건너뛰므로 자주 돌아도 중복 처리되지 않는다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class VoteCloseScheduler {

    private final VoteCloseService voteCloseService;
    private final Clock clock;

    /** 회차마다 트랜잭션을 따로 열어 한 방이 실패해도 다른 방은 계속 처리한다. */
    @Scheduled(cron = "0 */5 * * * *", zone = "Asia/Seoul")
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
