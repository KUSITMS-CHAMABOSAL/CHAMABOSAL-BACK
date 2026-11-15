package com.chamabosal.domain.user.entity;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

class UserTest {

    private static final LocalDateTime DELETED_AT = LocalDateTime.of(2026, 11, 15, 12, 0);

    @Test
    void 내_데이터를_삭제하면_삭제_시각을_남기고_식별키를_비운다() {
        User user = User.create("anon-key");

        user.deleteData(DELETED_AT);

        assertThat(user.isDeleted()).isTrue();
        assertThat(user.getDeletedAt()).isEqualTo(DELETED_AT);
        assertThat(user.getTossAnonKey()).isNull();
    }
}
