package com.chamabosal.domain.certification.photo;

import java.time.Duration;

/** 인증 사진 저장소 (CERT-04). 운영은 S3, 로컬·테스트는 메모리 구현을 쓴다. presign은 서명 계산만, 복사·삭제는 네트워크 호출이다. */
public interface PhotoStorage {

    /** 이 키에 contentType으로만 올릴 수 있는 업로드 URL */
    String presignUpload(String key, String contentType, Duration ttl);

    /** 조회 URL */
    String presignView(String key, Duration ttl);

    /** 원본이 없거나 접근할 수 없으면 false */
    boolean copy(String fromKey, String toKey);

    /** 없는 키를 지워도 성공한다. */
    void delete(String key);
}
