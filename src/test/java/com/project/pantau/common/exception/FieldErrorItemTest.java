package com.project.pantau.common.exception;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class FieldErrorItemTest {

    @Test
    @DisplayName("accessors return the values supplied to the constructor")
    void accessorsReturnConstructorValues() {
        FieldErrorItem item = new FieldErrorItem("email", "must not be blank");

        assertThat(item.field()).isEqualTo("email");
        assertThat(item.message()).isEqualTo("must not be blank");
    }

    @Test
    @DisplayName("equals and hashCode are based on field values")
    void equalsAndHashCode() {
        FieldErrorItem a = new FieldErrorItem("email", "must not be blank");
        FieldErrorItem b = new FieldErrorItem("email", "must not be blank");
        FieldErrorItem c = new FieldErrorItem("name", "must not be blank");

        assertThat(a).isEqualTo(b);
        assertThat(a).hasSameHashCodeAs(b);
        assertThat(a).isNotEqualTo(c);
    }

    @Test
    @DisplayName("toString contains field and message")
    void toStringContainsFields() {
        FieldErrorItem item = new FieldErrorItem("email", "must not be blank");

        assertThat(item.toString())
                .contains("email")
                .contains("must not be blank");
    }
}
