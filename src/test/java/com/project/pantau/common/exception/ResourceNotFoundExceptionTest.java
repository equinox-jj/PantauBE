package com.project.pantau.common.exception;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import static org.assertj.core.api.Assertions.assertThat;

class ResourceNotFoundExceptionTest {

    @Test
    @DisplayName("sets message and NOT_FOUND status")
    void setsMessageAndStatus() {
        ResourceNotFoundException ex = new ResourceNotFoundException("resource not found");

        assertThat(ex.getMessage()).isEqualTo("resource not found");
        assertThat(ex.getStatus()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    @DisplayName("extends ApiException")
    void extendsApiException() {
        ResourceNotFoundException ex = new ResourceNotFoundException("resource not found");

        assertThat(ex).isInstanceOf(ApiException.class);
        assertThat(ex).isInstanceOf(RuntimeException.class);
    }
}
