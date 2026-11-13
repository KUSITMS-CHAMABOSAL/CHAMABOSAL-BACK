package com.chamabosal.support;

import com.chamabosal.global.config.ClockConfig;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/** 시나리오마다 서버 시각을 바꿀 수 있는 Clock. 테스트 클래스에 `@Import(MutableClock.Config.class)`로 쓴다. */
public class MutableClock extends Clock {

    private volatile Instant instant = Instant.now();

    public void set(LocalDateTime time) {
        instant = time.atZone(ClockConfig.KST).toInstant();
    }

    @Override
    public ZoneId getZone() {
        return ClockConfig.KST;
    }

    @Override
    public Clock withZone(ZoneId zone) {
        throw new UnsupportedOperationException();
    }

    @Override
    public Instant instant() {
        return instant;
    }

    @TestConfiguration
    public static class Config {

        @Bean
        @Primary
        MutableClock mutableClock() {
            return new MutableClock();
        }
    }
}
