package com.project.pantau.common.exception;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import static org.assertj.core.api.Assertions.assertThat;

class ValidationExceptionTest {

    @Test
    @DisplayName("sets message and UNPROCESSABLE_CONTENT status")
    void setsMessageAndStatus() {
        ValidationException ex = new ValidationException("validation failed");

        assertThat(ex.getMessage()).isEqualTo("validation failed");
        assertThat(ex.getStatus()).isEqualTo(HttpStatus.UNPROCESSABLE_CONTENT);
    }

    @Test
    @DisplayName("extends ApiException")
    void extendsApiException() {
        ValidationException ex = new ValidationException("validation failed");

        assertThat(ex).isInstanceOf(ApiException.class);
        assertThat(ex).isInstanceOf(RuntimeException.class);
    }
}
