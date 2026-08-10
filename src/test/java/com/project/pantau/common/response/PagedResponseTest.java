package com.project.pantau.common.response;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * PagedResponse is a plain record with no custom logic, so this is a light
 * smoke test of its generated accessors and equality contract.
 */
class PagedResponseTest {

    @Test
    @DisplayName("exposes the constructed items and meta via accessors")
    void accessorsReturnConstructedValues() {
        List<String> items = List.of("a", "b", "c");
        PageMeta meta = new PageMeta(10, 0L, 3L, false);

        PagedResponse<String> response = new PagedResponse<>(items, meta);

        assertThat(response.items()).containsExactly("a", "b", "c");
        assertThat(response.meta()).isEqualTo(meta);
    }

    @Test
    @DisplayName("supports an empty items list")
    void supportsEmptyItems() {
        PagedResponse<String> response = new PagedResponse<>(List.of(), new PageMeta(10, 0L, 0L, false));

        assertThat(response.items()).isEmpty();
    }

    @Test
    @DisplayName("two instances with equal fields are equal")
    void equalsAndHashCode() {
        PageMeta meta = new PageMeta(10, 0L, 3L, false);
        PagedResponse<String> a = new PagedResponse<>(List.of("x"), meta);
        PagedResponse<String> b = new PagedResponse<>(List.of("x"), meta);

        assertThat(a).isEqualTo(b);
        assertThat(a).hasSameHashCodeAs(b);
    }
}
