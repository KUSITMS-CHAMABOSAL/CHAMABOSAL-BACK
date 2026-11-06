package com.chamabosal.domain.user.entity;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class UserTest {

    private static final LocalDateTime DELETED_AT = LocalDateTime.of(2026, 5, 6, 12, 0);

    @Test
    void 삭제_후_정확히_6개월이_되는_시각까지_복구할_수_있다() {
        User user = deletedUser();

        assertThat(user.isRestorable(DELETED_AT.plusMonths(6))).isTrue();
    }

    @Test
    void 삭제_후_6개월이_지나면_복구할_수_없다() {
        User user = deletedUser();

        assertThat(user.isRestorable(DELETED_AT.plusMonths(6).plusNanos(1))).isFalse();
    }

    private User deletedUser() {
        User user = User.create("anon-key");
        ReflectionTestUtils.setField(user, "deletedAt", DELETED_AT);
        return user;
    }
}
