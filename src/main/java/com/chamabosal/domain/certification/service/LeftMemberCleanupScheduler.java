package com.chamabosal.domain.certification.service;

import com.chamabosal.domain.certification.repository.CertificationRepository;
import com.chamabosal.domain.room.entity.RoomMember;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 나간 지 6개월이 지난 멤버의 인증에서 사진을 지우고 한 줄과 쓴 금액을 비운다 (CLEAN-01). 나간 유형은 가리지 않고,
 * 투표 결과는 그대로 둔다. 6개월은 다시 들어오기와 같은 RoomMember 기준으로 판단한다.
 * 사진은 회차 종료 7일 뒤 삭제(CERT-04)와 별개로, 그때까지 남아 있는 사진을 지운다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LeftMemberCleanupScheduler {

    private final CertificationRepository certificationRepository;
    private final LeftMemberRecordCleaner leftMemberRecordCleaner;
    private final Clock clock;

    @Scheduled(cron = "0 10 4 * * *", zone = "Asia/Seoul")
    @Transactional
    public void clearExpiredRecords() {
        LocalDateTime now = LocalDateTime.now(clock);
        List<Long> memberIds = certificationRepository
                .findMembersWithRecordsLeftBefore(RoomMember.retentionCandidateBefore(now)).stream()
                .filter(member -> member.isPastRetention(now))
                .map(RoomMember::getId)
                .toList();
        if (memberIds.isEmpty()) {
            return;
        }
        LeftMemberRecordCleaner.Result result = leftMemberRecordCleaner.clear(memberIds, now);
        log.info("떠난 보살 인증 기록 {}건, 사진 {}장 정리", result.cleared(), result.photos());
    }
}
