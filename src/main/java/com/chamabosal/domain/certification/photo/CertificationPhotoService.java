package com.chamabosal.domain.certification.photo;

import com.chamabosal.domain.certification.dto.response.PhotoUploadUrlResponse;
import com.chamabosal.domain.certification.entity.Certification;
import java.time.Duration;
import java.util.UUID;
import java.util.function.Function;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * 인증 사진 (CERT-04). 저장소 복사·삭제는 네트워크 호출이라 트랜잭션 밖에서 한다 (CERT-04 11번).
 * 복사는 등록·수정 전에 하고 실패하면 복사본을 지우며, 바꾸거나 지운 사진은 커밋 뒤에 지운다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CertificationPhotoService {

    static final Duration UPLOAD_TTL = Duration.ofMinutes(5);
    static final Duration VIEW_TTL = Duration.ofMinutes(30);
    private static final String TMP_PREFIX = "tmp/";
    private static final String PHOTO_PREFIX = "certifications/";

    private final PhotoStorage storage;

    /** 임시 키에 사용자 ID를 넣어 내가 받은 키만 쓰게 한다 (CERT-04 9번). */
    public PhotoUploadUrlResponse issueUploadUrl(Long userId, String contentType) {
        String key = TMP_PREFIX + userId + "/" + UUID.randomUUID();
        return new PhotoUploadUrlResponse(storage.presignUpload(key, contentType, UPLOAD_TTL), key, UPLOAD_TTL.toSeconds());
    }

    /** 내 임시 사진을 정식 경로로 옮긴 뒤 등록·수정을 하고, 등록·수정이 실패하면 옮긴 사진을 지운다. 트랜잭션 밖에서 부른다. */
    public <T> T withPhoto(Long userId, String imageKey, Function<PreparedPhoto, T> action) {
        PreparedPhoto photo = prepare(userId, imageKey);
        try {
            return action.apply(photo);
        } catch (RuntimeException e) {
            if (photo.hasPhoto()) {
                deleteQuietly(photo.key());
            }
            throw e;
        }
    }

    /** 커밋된 뒤에 지운다. 롤백되면 지우지 않는다. 트랜잭션 밖이면 바로 지운다. */
    public void deleteAfterCommit(String key) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            deleteQuietly(key);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                deleteQuietly(key);
            }
        });
    }

    /** 응답할 때마다 만드는 조회 URL. 사진이 없거나 지웠으면 null (CERT-04 4번). 오늘 탭(ROOM-02)도 이 메서드를 쓴다. */
    public String viewUrl(Certification certification) {
        if (certification == null || !certification.hasImage()) {
            return null;
        }
        return storage.presignView(certification.getImageKey(), VIEW_TTL);
    }

    private PreparedPhoto prepare(Long userId, String imageKey) {
        if (imageKey == null) {
            return PreparedPhoto.NONE;
        }
        if (!imageKey.startsWith(TMP_PREFIX + userId + "/")) {
            return PreparedPhoto.INVALID;
        }
        String key = PHOTO_PREFIX + UUID.randomUUID();
        return storage.copy(imageKey, key) ? PreparedPhoto.of(key) : PreparedPhoto.INVALID;
    }

    /** 지우기에 실패해도 요청은 성공시킨다. 남은 사진은 에러 로그로 알린다. */
    private void deleteQuietly(String key) {
        try {
            storage.delete(key);
        } catch (RuntimeException e) {
            log.error("인증 사진을 지우지 못했습니다. key={}", key, e);
        }
    }
}
