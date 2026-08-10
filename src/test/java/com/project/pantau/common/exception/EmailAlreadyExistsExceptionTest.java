package com.project.pantau.common.exception;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import static org.assertj.core.api.Assertions.assertThat;

class EmailAlreadyExistsExceptionTest {

    @Test
    @DisplayName("sets message and CONFLICT status")
    void setsMessageAndStatus() {
        EmailAlreadyExistsException ex = new EmailAlreadyExistsException("email already exists");

        assertThat(ex.getMessage()).isEqualTo("email already exists");
        assertThat(ex.getStatus()).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    @DisplayName("extends ApiException")
    void extendsApiException() {
        EmailAlreadyExistsException ex = new EmailAlreadyExistsException("email already exists");

        assertThat(ex).isInstanceOf(ApiException.class);
        assertThat(ex).isInstanceOf(RuntimeException.class);
    }
}
