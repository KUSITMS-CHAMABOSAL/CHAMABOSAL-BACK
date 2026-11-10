package com.chamabosal.global.common.exception;

import static org.assertj.core.api.Assertions.assertThat;

import com.chamabosal.global.common.response.ErrorResponse;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Valid;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.constraints.NotBlank;
import java.time.Clock;
import org.junit.jupiter.api.Test;

class ConstraintViolationHandlingTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();
    private final GlobalExceptionHandler handler = new GlobalExceptionHandler(event -> {}, Clock.systemUTC());

    @Test
    void 중첩_경로여도_field에는_마지막_필드명만_담긴다() {
        ConstraintViolationException e = new ConstraintViolationException(
                validator.validate(new Outer(new Inner(""))));

        ErrorResponse body = handler.handleConstraintViolationException(e).getBody();

        assertThat(body.code()).isEqualTo("COMMON_002");
        assertThat(body.field()).isEqualTo("title");
    }

    record Outer(@Valid Inner inner) {
    }

    record Inner(@NotBlank String title) {
    }
}
