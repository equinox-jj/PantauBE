package com.project.pantau.common.response;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * PageMeta is a plain record with no computed fields or custom logic (the
 * caller is responsible for computing hasNext), so this is a light smoke
 * test of its generated accessors and equality contract.
 */
class PageMetaTest {

    @Test
    @DisplayName("exposes the constructed limit, offset, total and hasNext via accessors")
    void accessorsReturnConstructedValues() {
        PageMeta meta = new PageMeta(10, 20L, 100L, true);

        assertThat(meta.limit()).isEqualTo(10);
        assertThat(meta.offset()).isEqualTo(20L);
        assertThat(meta.total()).isEqualTo(100L);
        assertThat(meta.hasNext()).isTrue();
    }

    @Test
    @DisplayName("two instances with equal fields are equal")
    void equalsAndHashCode() {
        PageMeta a = new PageMeta(10, 0L, 5L, false);
        PageMeta b = new PageMeta(10, 0L, 5L, false);

        assertThat(a).isEqualTo(b);
        assertThat(a).hasSameHashCodeAs(b);
    }
}
