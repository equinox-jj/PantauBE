package com.project.pantau.common.pagination;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OffsetPageableTest {

    @Test
    @DisplayName("constructor rejects a negative offset")
    void constructor_rejectsNegativeOffset() {
        assertThatThrownBy(() -> new OffsetPageable(-1, 10, Sort.unsorted()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Offset must not be negative");
    }

    @Test
    @DisplayName("constructor rejects a limit of zero")
    void constructor_rejectsZeroLimit() {
        assertThatThrownBy(() -> new OffsetPageable(0, 0, Sort.unsorted()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Limit must be greater than 0");
    }

    @Test
    @DisplayName("constructor rejects a negative limit")
    void constructor_rejectsNegativeLimit() {
        assertThatThrownBy(() -> new OffsetPageable(0, -5, Sort.unsorted()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Limit must be greater than 0");
    }

    @Test
    @DisplayName("constructor defaults a null sort to Sort.unsorted()")
    void constructor_defaultsNullSortToUnsorted() {
        OffsetPageable pageable = new OffsetPageable(0, 10, null);

        assertThat(pageable.getSort()).isEqualTo(Sort.unsorted());
        assertThat(pageable.sort()).isEqualTo(Sort.unsorted());
    }

    @Test
    @DisplayName("of() factory constructs the same as the canonical constructor")
    void of_constructsEquivalentInstance() {
        Sort sort = Sort.by("createdAt").descending();

        OffsetPageable pageable = OffsetPageable.of(20, 10, sort);

        assertThat(pageable).isEqualTo(new OffsetPageable(20, 10, sort));
    }

    @Test
    @DisplayName("getOffset returns the raw offset, not page-aligned")
    void getOffset_returnsRawOffset() {
        OffsetPageable pageable = new OffsetPageable(25, 10, Sort.unsorted());

        assertThat(pageable.getOffset()).isEqualTo(25L);
    }

    @Test
    @DisplayName("getPageSize returns the limit")
    void getPageSize_returnsLimit() {
        OffsetPageable pageable = new OffsetPageable(0, 15, Sort.unsorted());

        assertThat(pageable.getPageSize()).isEqualTo(15);
    }

    @Test
    @DisplayName("getPageNumber is the integer division of offset by limit")
    void getPageNumber_isIntegerDivisionOfOffsetByLimit() {
        assertThat(new OffsetPageable(0, 10, Sort.unsorted()).getPageNumber()).isEqualTo(0);
        assertThat(new OffsetPageable(9, 10, Sort.unsorted()).getPageNumber()).isEqualTo(0);
        assertThat(new OffsetPageable(10, 10, Sort.unsorted()).getPageNumber()).isEqualTo(1);
        assertThat(new OffsetPageable(25, 10, Sort.unsorted()).getPageNumber()).isEqualTo(2);
    }

    @Test
    @DisplayName("getSort returns the provided sort")
    void getSort_returnsProvidedSort() {
        Sort sort = Sort.by("title").ascending();

        OffsetPageable pageable = new OffsetPageable(0, 10, sort);

        assertThat(pageable.getSort()).isEqualTo(sort);
    }

    @Test
    @DisplayName("next() advances the offset by exactly one limit's worth of rows")
    void next_advancesOffsetByLimit() {
        OffsetPageable pageable = new OffsetPageable(5, 10, Sort.unsorted());

        Pageable next = pageable.next();

        assertThat(next.getOffset()).isEqualTo(15L);
        assertThat(next.getPageSize()).isEqualTo(10);
        assertThat(next.getSort()).isEqualTo(Sort.unsorted());
    }

    @Test
    @DisplayName("previousOrFirst() steps back by limit, clamped at 0, when there is a previous page")
    void previousOrFirst_stepsBackWhenOffsetPositive() {
        OffsetPageable pageable = new OffsetPageable(25, 10, Sort.unsorted());

        Pageable previous = pageable.previousOrFirst();

        assertThat(previous.getOffset()).isEqualTo(15L);
    }

    @Test
    @DisplayName("previousOrFirst() clamps to offset 0 rather than going negative")
    void previousOrFirst_clampsAtZero() {
        OffsetPageable pageable = new OffsetPageable(5, 10, Sort.unsorted());

        Pageable previous = pageable.previousOrFirst();

        assertThat(previous.getOffset()).isEqualTo(0L);
    }

    @Test
    @DisplayName("previousOrFirst() returns first() when there is no previous page")
    void previousOrFirst_returnsFirstWhenOffsetZero() {
        OffsetPageable pageable = new OffsetPageable(0, 10, Sort.unsorted());

        Pageable previous = pageable.previousOrFirst();

        assertThat(previous.getOffset()).isEqualTo(0L);
        assertThat(previous.getPageSize()).isEqualTo(10);
    }

    @Test
    @DisplayName("first() resets the offset to 0 while keeping limit and sort")
    void first_resetsOffsetToZero() {
        Sort sort = Sort.by("id");
        OffsetPageable pageable = new OffsetPageable(50, 20, sort);

        Pageable first = pageable.first();

        assertThat(first.getOffset()).isEqualTo(0L);
        assertThat(first.getPageSize()).isEqualTo(20);
        assertThat(first.getSort()).isEqualTo(sort);
    }

    @Test
    @DisplayName("withPage() computes offset as pageNumber * limit")
    void withPage_computesOffsetFromPageNumber() {
        OffsetPageable pageable = new OffsetPageable(0, 10, Sort.unsorted());

        Pageable page3 = pageable.withPage(3);

        assertThat(page3.getOffset()).isEqualTo(30L);
        assertThat(page3.getPageNumber()).isEqualTo(3);
    }

    @Test
    @DisplayName("withPage(0) produces offset 0")
    void withPage_zeroProducesOffsetZero() {
        OffsetPageable pageable = new OffsetPageable(40, 10, Sort.unsorted());

        Pageable page0 = pageable.withPage(0);

        assertThat(page0.getOffset()).isEqualTo(0L);
    }

    @Test
    @DisplayName("hasPrevious() is false at offset 0 and true for any positive offset")
    void hasPrevious_reflectsOffset() {
        assertThat(new OffsetPageable(0, 10, Sort.unsorted()).hasPrevious()).isFalse();
        assertThat(new OffsetPageable(1, 10, Sort.unsorted()).hasPrevious()).isTrue();
        assertThat(new OffsetPageable(10, 10, Sort.unsorted()).hasPrevious()).isTrue();
    }

    @Test
    @DisplayName("implements Spring Data's Pageable")
    void implementsPageable() {
        OffsetPageable pageable = new OffsetPageable(0, 10, Sort.unsorted());

        assertThat(pageable).isInstanceOf(Pageable.class);
    }
}
