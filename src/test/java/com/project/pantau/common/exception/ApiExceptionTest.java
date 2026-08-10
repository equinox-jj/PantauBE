package com.project.pantau.common.exception;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import static org.assertj.core.api.Assertions.assertThat;

class ApiExceptionTest {

    @Test
    @DisplayName("constructor sets status and message")
    void constructorSetsStatusAndMessage() {
        TestApiException ex = new TestApiException(HttpStatus.I_AM_A_TEAPOT, "teapot error");

        assertThat(ex.getStatus()).isEqualTo(HttpStatus.I_AM_A_TEAPOT);
        assertThat(ex.getMessage()).isEqualTo("teapot error");
    }

    @Test
    @DisplayName("is a RuntimeException")
    void isRuntimeException() {
        TestApiException ex = new TestApiException(HttpStatus.BAD_REQUEST, "bad");

        assertThat(ex).isInstanceOf(RuntimeException.class);
    }

    @Test
    @DisplayName("no cause is set when only message is provided")
    void noCauseByDefault() {
        TestApiException ex = new TestApiException(HttpStatus.BAD_REQUEST, "bad");

        assertThat(ex.getCause()).isNull();
    }

    /**
     * ApiException is abstract; use a minimal concrete subclass so we can
     * exercise the protected constructor directly.
     */
    private static class TestApiException extends ApiException {
        TestApiException(HttpStatus status, String message) {
            super(status, message);
        }
    }
}
