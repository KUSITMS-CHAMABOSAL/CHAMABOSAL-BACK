package com.chamabosal.domain.demo.service;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

/** 시연 설정 값 확인 (코드리뷰 반영). 시나리오가 아니라 설정 실수를 기동 때 막는지 본다. */
class DemoSettingsTest {

    private static final String KEY = "demo-settings-key-0123456789abcdefghij";

    @Test
    void 키_앞뒤_공백은_지우고_비교한다() {
        DemoSettings settings = new DemoSettings(true, KEY + "\n", "");

        assertThatCode(() -> settings.checkAccess(KEY)).doesNotThrowAnyException();
    }

    @Test
    void 같은_userId를_두_시연_번호에_넣으면_기동하지_않는다() {
        assertThatThrownBy(() -> new DemoSettings(true, KEY, "1:12:보살이,2:12:참는중"))
                .isInstanceOf(IllegalStateException.class);
    }
}
