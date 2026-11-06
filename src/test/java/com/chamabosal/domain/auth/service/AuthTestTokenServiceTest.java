package com.chamabosal.domain.auth.service;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class AuthTestTokenServiceTest {

    @Test
    void 기능을_켰는데_비밀값이_비어_있으면_기동에_실패한다() {
        assertThatThrownBy(() -> new AuthTestTokenService(null, null, true, " "))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void 기능을_켰는데_비밀값이_32자_미만이면_기동에_실패한다() {
        assertThatThrownBy(() -> new AuthTestTokenService(null, null, true, "a".repeat(31)))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void 기능을_켜고_비밀값이_32자면_기동한다() {
        assertThatCode(() -> new AuthTestTokenService(null, null, true, "a".repeat(32)))
                .doesNotThrowAnyException();
    }

    @Test
    void 기능이_꺼져_있으면_비밀값이_짧아도_기동한다() {
        assertThatCode(() -> new AuthTestTokenService(null, null, false, ""))
                .doesNotThrowAnyException();
    }
}
