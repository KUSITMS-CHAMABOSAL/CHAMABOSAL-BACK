package com.chamabosal.domain.room.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import com.chamabosal.domain.certification.entity.Certification;
import com.chamabosal.domain.certification.entity.CertificationResult;
import com.chamabosal.domain.certification.photo.MemoryPhotoStorage;
import com.chamabosal.domain.certification.photo.PhotoStorage;
import com.chamabosal.domain.certification.service.LeftMemberCleanupScheduler;
import com.chamabosal.domain.room.entity.LeaveType;
import com.chamabosal.domain.room.entity.Room;
import com.chamabosal.domain.room.entity.RoomMember;
import com.chamabosal.domain.round.entity.Round;
import com.chamabosal.domain.round.entity.RoundStatus;
import java.time.LocalDateTime;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** 떠난 보살 기록 정리 (CLEAN-01). 사진은 테스트용 메모리 저장소에서 지워졌는지 본다. */
class LeftMemberCleanupTest extends RoomTestSupport {

    @Autowired
    LeftMemberCleanupScheduler leftMemberCleanupScheduler;

    @Autowired
    PhotoStorage photoStorage;

    @AfterEach
    void clearPhotos() {
        storage().clear();
    }

    @Test
    void CLEAN_01_1_나간_지_6개월이_지난_멤버의_사진을_지우고_한_줄과_금액을_비운다() {
        Room room = createRoom(4);
        RoomMember a = addMember(room, newUser(), "민지");
        Certification certification = certifyFailed(room, a);
        leave(a.getId(), NOW.minusMonths(6).minusMinutes(1), LeaveType.LEFT);

        leftMemberCleanupScheduler.clearExpiredRecords();

        assertCleared(certification);
    }

    @Test
    void CLEAN_01_2_나간_지_6개월이_지나지_않은_멤버의_기록은_그대로다() {
        Room room = createRoom(4);
        RoomMember a = addMember(room, newUser(), "민지");
        Certification certification = certifyFailed(room, a);
        leave(a.getId(), NOW.minusMonths(5), LeaveType.LEFT);

        leftMemberCleanupScheduler.clearExpiredRecords();

        assertKept(certification);
    }

    @Test
    void CLEAN_01_3_다시_들어온_멤버의_기록은_그대로다() {
        Room room = createRoom(4);
        RoomMember a = addMember(room, newUser(), "민지");
        Certification certification = certifyFailed(room, a);
        a.leave(NOW.minusMonths(6).minusDays(1), LeaveType.LEFT);
        a.rejoin("민지", a.getColor(), NOW.minusMonths(1));
        roomMemberRepository.save(a);

        leftMemberCleanupScheduler.clearExpiredRecords();

        assertKept(certification);
    }

    @Test
    void CLEAN_01_4_내_데이터를_삭제한_지_6개월이_지난_멤버의_한_줄과_금액을_비운다() {
        Room room = createRoom(4);
        RoomMember a = addMember(room, newUser(), "민지");
        Certification certification = certifyFailed(room, a);
        leave(a.getId(), NOW.minusMonths(6).minusMinutes(1), LeaveType.DATA_DELETED);

        leftMemberCleanupScheduler.clearExpiredRecords();

        assertCleared(certification);
    }

    @Test
    void CLEAN_01_5_이미_정리한_기록을_다시_실행해도_오류_없이_끝난다() {
        Room room = createRoom(4);
        RoomMember a = addMember(room, newUser(), "민지");
        Certification certification = certifyFailed(room, a);
        leave(a.getId(), NOW.minusMonths(6).minusMinutes(1), LeaveType.LEFT);
        leftMemberCleanupScheduler.clearExpiredRecords();

        assertThatCode(() -> leftMemberCleanupScheduler.clearExpiredRecords()).doesNotThrowAnyException();
        assertCleared(certification);
    }

    @Test
    void CLEAN_01_7_내보내진_지_6개월이_지난_멤버의_한_줄과_금액을_비운다() {
        Room room = createRoom(4);
        RoomMember a = addMember(room, newUser(), "민지");
        Certification certification = certifyFailed(room, a);
        leave(a.getId(), NOW.minusMonths(6).minusMinutes(1), LeaveType.KICKED);

        leftMemberCleanupScheduler.clearExpiredRecords();

        assertCleared(certification);
    }

    @Test
    void 월말에_나간_멤버도_다시_들어올_수_없게_된_시각부터_정리_대상이다() {
        Room room = createRoom(4);
        RoomMember a = addMember(room, newUser(), "민지");
        Certification certification = certifyFailed(room, a);
        LocalDateTime leftAt = LocalDateTime.of(2026, 8, 31, 12, 0);
        leave(a.getId(), leftAt, LeaveType.LEFT);
        LocalDateTime afterRetention = LocalDateTime.of(2027, 2, 28, 12, 10);
        clock.set(afterRetention);
        assertThat(roomMemberRepository.findById(a.getId()).orElseThrow().canRejoin(afterRetention)).isFalse();

        leftMemberCleanupScheduler.clearExpiredRecords();

        assertCleared(certification);
    }

    @Test
    void 사진만_남은_멤버도_사진을_지운다() {
        Room room = createRoom(4);
        RoomMember a = addMember(room, newUser(), "민지");
        Certification certification = withPhoto(certify(room, a, CertificationResult.ENDURED, null, null), a);
        leave(a.getId(), NOW.minusMonths(6).minusMinutes(1), LeaveType.LEFT);

        leftMemberCleanupScheduler.clearExpiredRecords();

        assertPhotoDeleted(certification);
    }

    /** 나가기 전, 7개월 전 회차에 `못 참았어요`를 사진·한 줄·금액과 함께 남긴다. */
    private Certification certifyFailed(Room room, RoomMember member) {
        return withPhoto(certify(room, member, CertificationResult.FAILED, "치킨 시켰어요", 23000), member);
    }

    private Certification certify(Room room, RoomMember member, CertificationResult result, String memo,
                                  Integer spentAmount) {
        LocalDateTime certifiedAt = NOW.minusMonths(7);
        Round round = saveRound(room, certifiedAt.toLocalDate(), RoundStatus.ANNOUNCED);
        return certificationRepository.save(Certification.create(round, member, result, memo, spentAmount, certifiedAt));
    }

    private Certification withPhoto(Certification certification, RoomMember member) {
        String key = photoKey(member);
        storage().upload(key);
        certification.replaceImage(key);
        return certificationRepository.save(certification);
    }

    private void assertPhotoDeleted(Certification certification) {
        Certification reloaded = certificationRepository.findById(certification.getId()).orElseThrow();
        assertThat(reloaded.hasImage()).isFalse();
        assertThat(storage().contains(photoKey(certification.getMember()))).isFalse();
    }

    private static String photoKey(RoomMember member) {
        return "certifications/left-" + member.getId();
    }

    private MemoryPhotoStorage storage() {
        return (MemoryPhotoStorage) photoStorage;
    }

    private void assertCleared(Certification certification) {
        Certification reloaded = certificationRepository.findById(certification.getId()).orElseThrow();
        assertThat(reloaded.getMemo()).isNull();
        assertThat(reloaded.getSpentAmount()).isNull();
        assertThat(reloaded.getResult()).isEqualTo(CertificationResult.FAILED);
        assertPhotoDeleted(certification);
    }

    private void assertKept(Certification certification) {
        Certification reloaded = certificationRepository.findById(certification.getId()).orElseThrow();
        assertThat(reloaded.getMemo()).isEqualTo("치킨 시켰어요");
        assertThat(reloaded.getSpentAmount()).isEqualTo(23000);
        assertThat(reloaded.hasImage()).isTrue();
        assertThat(storage().contains(photoKey(certification.getMember()))).isTrue();
    }
}
