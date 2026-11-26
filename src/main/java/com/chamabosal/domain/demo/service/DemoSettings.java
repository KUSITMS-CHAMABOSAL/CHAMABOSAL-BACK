package com.chamabosal.domain.demo.service;

import com.chamabosal.global.common.exception.BaseErrorCode;
import com.chamabosal.global.common.exception.BaseException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Map;
import java.util.TreeMap;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 전시 시연 설정 (docs/spec/demo.md 공통). 꺼져 있으면 시연 처리는 모두 아무것도 하지 않는다.
 * 시연 번호는 `번호:userId:닉네임`을 쉼표로 이은 값으로 받는다. 예) `1:12:보살이,2:34:참는중`
 */
@Component
public class DemoSettings {

    /** 가짜 팀원의 토스 식별키 접두어. 토스가 주는 키와 테스트 토큰(`test-`)과 겹치지 않는다. */
    public static final String FAKE_KEY_PREFIX = "demo-";
    private static final int MIN_KEY_LENGTH = 32;
    private static final int MAX_NICKNAME_LENGTH = 8;

    private final boolean enabled;
    private final byte[] key;
    private final Map<Integer, Slot> slots;

    public DemoSettings(
            @Value("${demo.enabled}") boolean enabled,
            @Value("${demo.key}") String key,
            @Value("${demo.slots}") String slots
    ) {
        // secret·env 파일 끝의 줄바꿈·공백 때문에 맞는 키가 거부되지 않도록 길이 확인과 비교에 같은 지운 값을 쓴다 (코드리뷰 반영).
        String strippedKey = key.strip();
        // 배포 스크립트(cd.yml)와 같게 앞뒤 공백을 지운 UTF-8 바이트 수로 센다 (코드리뷰 반영).
        if (enabled && strippedKey.getBytes(StandardCharsets.UTF_8).length < MIN_KEY_LENGTH) {
            throw new IllegalStateException("시연 모드를 켜려면 32바이트 이상의 DEMO_KEY가 필요합니다.");
        }
        this.enabled = enabled;
        this.key = strippedKey.getBytes(StandardCharsets.UTF_8);
        this.slots = parse(slots);
    }

    public boolean isEnabled() {
        return enabled;
    }

    /** 꺼져 있으면 없는 API처럼, 키가 틀리면 인증 실패로 막는다 (DEMO-01 1·2번). */
    public void checkAccess(String requestKey) {
        if (!enabled) {
            throw new BaseException(BaseErrorCode.ENDPOINT_NOT_FOUND);
        }
        if (requestKey == null || !MessageDigest.isEqual(key, requestKey.getBytes(StandardCharsets.UTF_8))) {
            throw new BaseException(BaseErrorCode.UNAUTHENTICATED);
        }
    }

    public Slot getSlot(int number) {
        Slot slot = slots.get(number);
        if (slot == null) {
            throw new BaseException(BaseErrorCode.DEMO_SLOT_NOT_FOUND);
        }
        return slot;
    }

    public boolean isDemoAccount(Long userId) {
        return slots.values().stream().anyMatch(slot -> slot.userId().equals(userId));
    }

    private static Map<Integer, Slot> parse(String raw) {
        Map<Integer, Slot> parsed = new TreeMap<>();
        if (raw == null || raw.isBlank()) {
            return parsed;
        }
        for (String entry : raw.split(",")) {
            String[] parts = entry.strip().split(":", 3);
            if (parts.length != 3 || parts[2].isBlank() || parts[2].strip().length() > MAX_NICKNAME_LENGTH) {
                throw new IllegalStateException("DEMO_SLOTS 형식은 번호:userId:닉네임(1~8자) 입니다: " + entry);
            }
            int number;
            long userId;
            try {
                number = Integer.parseInt(parts[0].strip());
                userId = Long.parseLong(parts[1].strip());
            } catch (NumberFormatException e) {
                throw new IllegalStateException("DEMO_SLOTS 형식은 번호:userId:닉네임(1~8자) 입니다: " + entry, e);
            }
            if (parsed.put(number, new Slot(number, userId, parts[2].strip())) != null) {
                throw new IllegalStateException("DEMO_SLOTS에 같은 시연 번호가 두 번 있습니다: " + number);
            }
        }
        // 한 계정을 두 번호가 같이 쓰면 한 번호를 리셋할 때 다른 폰의 방과 공양미까지 지운다 (DEMO-01 12번, 코드리뷰 반영).
        long distinctUsers = parsed.values().stream().map(Slot::userId).distinct().count();
        if (distinctUsers != parsed.size()) {
            throw new IllegalStateException("DEMO_SLOTS에 같은 userId가 두 번호에 있습니다. 폰마다 다른 계정을 쓰세요.");
        }
        return parsed;
    }

    public record Slot(int number, Long userId, String nickname) {
    }
}
