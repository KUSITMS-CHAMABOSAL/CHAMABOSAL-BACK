package com.chamabosal.domain.auth.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class AuthTestTokenServiceTest {

    @Test
    void 기능을_켰는데_비밀값이_비어_있으면_기동에_실패한다() {
        assertThatThrownBy(() -> new AuthTestTokenService(null, null, true, " "))
                .isInstanceOf(IllegalStateException.class);
    }
}
