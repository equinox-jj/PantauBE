package com.project.pantau.common.response;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * ApiResponse is a plain record with no custom logic (no compact constructor
 * validation, no static factories), so this is a light smoke test of its
 * generated accessors and equality contract rather than exhaustive coverage.
 */
class ApiResponseTest {

    @Test
    @DisplayName("exposes the constructed status, message and data via accessors")
    void accessorsReturnConstructedValues() {
        ApiResponse<String> response = new ApiResponse<>(true, "ok", "payload");

        assertThat(response.status()).isTrue();
        assertThat(response.message()).isEqualTo("ok");
        assertThat(response.data()).isEqualTo("payload");
    }

    @Test
    @DisplayName("supports a null data payload, e.g. for error responses")
    void supportsNullData() {
        ApiResponse<String> response = new ApiResponse<>(false, "error occurred", null);

        assertThat(response.status()).isFalse();
        assertThat(response.message()).isEqualTo("error occurred");
        assertThat(response.data()).isNull();
    }

    @Test
    @DisplayName("two instances with equal fields are equal")
    void equalsAndHashCode() {
        ApiResponse<String> a = new ApiResponse<>(true, "ok", "payload");
        ApiResponse<String> b = new ApiResponse<>(true, "ok", "payload");

        assertThat(a).isEqualTo(b);
        assertThat(a).hasSameHashCodeAs(b);
    }
}
