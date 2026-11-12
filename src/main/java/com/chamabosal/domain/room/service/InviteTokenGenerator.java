package com.chamabosal.domain.room.service;

import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.util.Base64;

/** 랜덤 생성을 테스트에서 바꿔 끼울 수 있게 서비스와 분리했다. */
@Component
public class InviteTokenGenerator {

    private static final int TOKEN_BYTES = 32;

    private final SecureRandom random = new SecureRandom();

    /** 링크 경로에 그대로 쓸 수 있게 URL-safe Base64로 만든다. 43자라 컬럼 길이 64 안에 들어간다. */
    public String generate() {
        byte[] bytes = new byte[TOKEN_BYTES];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
