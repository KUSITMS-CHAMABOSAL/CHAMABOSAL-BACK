package com.chamabosal.domain.certification.photo;

import com.chamabosal.global.common.exception.BaseErrorCode;
import com.chamabosal.global.common.exception.BaseException;

/**
 * 트랜잭션 전에 정식 경로로 옮겨 둔 사진. 키가 잘못됐어도 바로 거부하지 않고, 등록·수정이 다른 확인을 마친 뒤
 * checkValid로 거부한다 (CERT-01 예외, CERT-02 11번의 확인 순서).
 */
public record PreparedPhoto(String key, boolean isInvalid) {

    public static final PreparedPhoto NONE = new PreparedPhoto(null, false);
    public static final PreparedPhoto INVALID = new PreparedPhoto(null, true);

    private static final String INVALID_MESSAGE = "없거나 만료된 사진입니다.";

    public static PreparedPhoto of(String key) {
        return new PreparedPhoto(key, false);
    }

    public boolean hasPhoto() {
        return key != null;
    }

    public void checkValid() {
        if (isInvalid) {
            throw BaseException.withField(BaseErrorCode.INVALID_REQUEST, INVALID_MESSAGE, "imageKey");
        }
    }
}
