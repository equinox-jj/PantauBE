package com.project.pantau.service.impl;

import com.project.pantau.common.exception.ResourceNotFoundException;
import com.project.pantau.dto.category.CategoryResponse;
import com.project.pantau.entity.Category;
import com.project.pantau.mapper.CategoryMapper;
import com.project.pantau.repository.CategoryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CategoryServiceImplTest {

    @Mock
    private CategoryRepository categoryRepository;

    @Mock
    private CategoryMapper categoryMapper;

    private CategoryServiceImpl categoryService;

    @BeforeEach
    void setUp() {
        categoryService = new CategoryServiceImpl(categoryRepository, categoryMapper);
    }

    private Category buildCategory(Long id, String name, String slug, boolean active) {
        return Category.builder()
                .id(id)
                .name(name)
                .slug(slug)
                .isActive(active)
                .createdAt(LocalDateTime.of(2026, 1, 1, 0, 0))
                .build();
    }

    private CategoryResponse buildResponse(Category category) {
        return CategoryResponse.builder()
                .id(category.getId())
                .name(category.getName())
                .slug(category.getSlug())
                .isActive(category.isActive())
                .build();
    }

    @Test
    @DisplayName("getActiveCategories returns mapped list of active categories in repository order")
    void getActiveCategoriesReturnsMappedList() {
        var category1 = buildCategory(1L, "Roads", "roads", true);
        var category2 = buildCategory(2L, "Lighting", "lighting", true);
        var response1 = buildResponse(category1);
        var response2 = buildResponse(category2);

        when(categoryRepository.findByIsActiveTrueOrderByIdAsc()).thenReturn(List.of(category1, category2));
        when(categoryMapper.toResponse(category1)).thenReturn(response1);
        when(categoryMapper.toResponse(category2)).thenReturn(response2);

        var result = categoryService.getActiveCategories();

        assertThat(result).containsExactly(response1, response2);
    }

    @Test
    @DisplayName("getActiveCategories returns empty list when no active categories exist")
    void getActiveCategoriesReturnsEmptyList() {
        when(categoryRepository.findByIsActiveTrueOrderByIdAsc()).thenReturn(List.of());

        var result = categoryService.getActiveCategories();

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("getCategoryById returns mapped category when found")
    void getCategoryByIdSuccess() {
        var category = buildCategory(1L, "Roads", "roads", true);
        var response = buildResponse(category);

        when(categoryRepository.findById(1L)).thenReturn(Optional.of(category));
        when(categoryMapper.toResponse(category)).thenReturn(response);

        var result = categoryService.getCategoryById(1L);

        assertThat(result).isEqualTo(response);
        assertThat(result.id()).isEqualTo(1L);
        assertThat(result.name()).isEqualTo("Roads");
        assertThat(result.slug()).isEqualTo("roads");
        assertThat(result.isActive()).isTrue();
    }

    @Test
    @DisplayName("getCategoryById throws ResourceNotFoundException when category not found")
    void getCategoryByIdNotFound() {
        when(categoryRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> categoryService.getCategoryById(99L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("99");

        verify(categoryMapper, never()).toResponse(any(Category.class));
    }

    @Test
    @DisplayName("getCategoryBySlug returns mapped category when found")
    void getCategoryBySlugSuccess() {
        var category = buildCategory(2L, "Lighting", "lighting", false);
        var response = buildResponse(category);

        when(categoryRepository.findBySlug("lighting")).thenReturn(Optional.of(category));
        when(categoryMapper.toResponse(category)).thenReturn(response);

        var result = categoryService.getCategoryBySlug("lighting");

        assertThat(result).isEqualTo(response);
        assertThat(result.isActive()).isFalse();
    }

    @Test
    @DisplayName("getCategoryBySlug throws ResourceNotFoundException when slug not found")
    void getCategoryBySlugNotFound() {
        when(categoryRepository.findBySlug("unknown")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> categoryService.getCategoryBySlug("unknown"))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("unknown");

        verify(categoryMapper, never()).toResponse(any(Category.class));
    }
}
