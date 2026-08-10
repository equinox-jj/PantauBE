package com.project.pantau.mapper;

import com.project.pantau.dto.category.CategoryResponse;
import com.project.pantau.entity.Category;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CategoryMapperTest {

    private final CategoryMapper categoryMapper = Mappers.getMapper(CategoryMapper.class);

    @Test
    void toResponse_mapsAllFieldsIncludingActiveExpression() {
        Category category = Category.builder()
                .id(1L)
                .name("Pothole")
                .slug("pothole")
                .isActive(true)
                .createdAt(LocalDateTime.of(2026, 1, 1, 0, 0))
                .build();

        CategoryResponse response = categoryMapper.toResponse(category);

        assertThat(response).isNotNull();
        assertThat(response.id()).isEqualTo(1L);
        assertThat(response.name()).isEqualTo("Pothole");
        assertThat(response.slug()).isEqualTo("pothole");
        assertThat(response.isActive()).isTrue();
    }

    @Test
    void toResponse_mapsInactiveCategoryViaExpression() {
        Category category = Category.builder()
                .id(2L)
                .name("Streetlight")
                .slug("streetlight")
                .isActive(false)
                .createdAt(LocalDateTime.now())
                .build();

        CategoryResponse response = categoryMapper.toResponse(category);

        assertThat(response.isActive()).isFalse();
    }

    @Test
    void toResponse_returnsNullForNullInput() {
        assertThat(categoryMapper.toResponse((Category) null)).isNull();
    }

    @Test
    void toResponseList_mapsEachElementInOrder() {
        Category active = Category.builder()
                .id(1L).name("Pothole").slug("pothole").isActive(true).createdAt(LocalDateTime.now())
                .build();
        Category inactive = Category.builder()
                .id(2L).name("Streetlight").slug("streetlight").isActive(false).createdAt(LocalDateTime.now())
                .build();

        List<CategoryResponse> responses = categoryMapper.toResponse(List.of(active, inactive));

        assertThat(responses).hasSize(2);
        assertThat(responses.get(0).id()).isEqualTo(1L);
        assertThat(responses.get(0).isActive()).isTrue();
        assertThat(responses.get(1).id()).isEqualTo(2L);
        assertThat(responses.get(1).isActive()).isFalse();
    }

    @Test
    void toResponseList_returnsNullForNullInput() {
        assertThat(categoryMapper.toResponse((List<Category>) null)).isNull();
    }
}
