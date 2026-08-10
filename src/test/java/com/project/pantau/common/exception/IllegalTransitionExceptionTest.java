package com.project.pantau.common.exception;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import static org.assertj.core.api.Assertions.assertThat;

class IllegalTransitionExceptionTest {

    @Test
    @DisplayName("sets message and CONFLICT status")
    void setsMessageAndStatus() {
        IllegalTransitionException ex = new IllegalTransitionException("cannot transition state");

        assertThat(ex.getMessage()).isEqualTo("cannot transition state");
        assertThat(ex.getStatus()).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    @DisplayName("extends ApiException")
    void extendsApiException() {
        IllegalTransitionException ex = new IllegalTransitionException("cannot transition state");

        assertThat(ex).isInstanceOf(ApiException.class);
        assertThat(ex).isInstanceOf(RuntimeException.class);
    }
}
