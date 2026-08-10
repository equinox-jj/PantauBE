package com.project.pantau.common.exception;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import static org.assertj.core.api.Assertions.assertThat;

class BadRequestExceptionTest {

    @Test
    @DisplayName("sets message and BAD_REQUEST status")
    void setsMessageAndStatus() {
        BadRequestException ex = new BadRequestException("invalid input");

        assertThat(ex.getMessage()).isEqualTo("invalid input");
        assertThat(ex.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    @DisplayName("extends ApiException")
    void extendsApiException() {
        BadRequestException ex = new BadRequestException("invalid input");

        assertThat(ex).isInstanceOf(ApiException.class);
        assertThat(ex).isInstanceOf(RuntimeException.class);
    }
}
