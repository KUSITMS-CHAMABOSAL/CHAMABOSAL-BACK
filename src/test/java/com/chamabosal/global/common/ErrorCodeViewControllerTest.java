package com.chamabosal.global.common;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.chamabosal.global.common.exception.BaseErrorCode;
import com.chamabosal.global.config.ClockConfig;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(ErrorCodeViewController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(ClockConfig.class)
class ErrorCodeViewControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void ErrorCode_enum을_도메인별로_렌더링한다() throws Exception {
        mockMvc.perform(get("/docs/error-codes"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("COMMON (" + BaseErrorCode.values().length + ")")))
                .andExpect(content().string(containsString("COMMON_001")))
                .andExpect(content().string(containsString(BaseErrorCode.UNHANDLED_ERROR.getMessage())));
    }

    @Test
    void 상수별_본문이_있는_enum도_수집한다() throws Exception {
        mockMvc.perform(get("/docs/error-codes"))
                .andExpect(content().string(containsString("TEST_001")))
                .andExpect(content().string(containsString("테스트용 에러입니다.")));
    }

    @Test
    void 중복된_code가_있으면_예외가_발생한다() {
        assertThatThrownBy(() -> ErrorCodeViewController.validateNoDuplicateCodes(
                List.of(BaseErrorCode.ENDPOINT_NOT_FOUND, BaseErrorCode.ENDPOINT_NOT_FOUND)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("COMMON_001");
    }
}
