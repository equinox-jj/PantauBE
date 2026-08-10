package com.project.pantau.controller;

import com.project.pantau.common.exception.GlobalExceptionHandler;
import com.project.pantau.common.exception.ResourceNotFoundException;
import com.project.pantau.dto.category.CategoryResponse;
import com.project.pantau.service.CategoryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class CategoryControllerTest {

    @Mock
    private CategoryService categoryService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        var controller = new CategoryController(categoryService);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    private CategoryResponse buildResponse(Long id, String name, String slug) {
        return CategoryResponse.builder()
                .id(id)
                .name(name)
                .slug(slug)
                .isActive(true)
                .build();
    }

    @Test
    @DisplayName("GET /categories returns 200 with list of active categories")
    void getActiveCategoriesSuccess() throws Exception {
        var categories = List.of(
                buildResponse(1L, "Roads", "roads"),
                buildResponse(2L, "Lighting", "lighting")
        );
        when(categoryService.getActiveCategories()).thenReturn(categories);

        mockMvc.perform(get("/api/v1/categories"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(true))
                .andExpect(jsonPath("$.message").value("Successfully retrieved categories"))
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[0].name").value("Roads"))
                .andExpect(jsonPath("$.data[1].slug").value("lighting"));

        verify(categoryService).getActiveCategories();
    }

    @Test
    @DisplayName("GET /categories returns 200 with empty list when none active")
    void getActiveCategoriesEmpty() throws Exception {
        when(categoryService.getActiveCategories()).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/categories"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data.length()").value(0));
    }

    @Test
    @DisplayName("GET /categories/{id} returns 200 with category when found")
    void getCategoryByIdSuccess() throws Exception {
        var response = buildResponse(1L, "Roads", "roads");
        when(categoryService.getCategoryById(1L)).thenReturn(response);

        mockMvc.perform(get("/api/v1/categories/{id}", 1L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(true))
                .andExpect(jsonPath("$.message").value("Successfully retrieved category"))
                .andExpect(jsonPath("$.data.id").value(1))
                .andExpect(jsonPath("$.data.name").value("Roads"));
    }

    @Test
    @DisplayName("GET /categories/{id} returns 404 when category not found")
    void getCategoryByIdNotFound() throws Exception {
        when(categoryService.getCategoryById(99L))
                .thenThrow(new ResourceNotFoundException("Category with id 99 not found"));

        mockMvc.perform(get("/api/v1/categories/{id}", 99L))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(false))
                .andExpect(jsonPath("$.message").value("Category with id 99 not found"));
    }

    @Test
    @DisplayName("GET /categories/{id} returns 400 when id is not a valid number")
    void getCategoryByIdInvalidType() throws Exception {
        mockMvc.perform(get("/api/v1/categories/{id}", "abc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(false));
    }

    @Test
    @DisplayName("GET /categories/slug/{slug} returns 200 with category when found")
    void getCategoryBySlugSuccess() throws Exception {
        var response = buildResponse(2L, "Lighting", "lighting");
        when(categoryService.getCategoryBySlug("lighting")).thenReturn(response);

        mockMvc.perform(get("/api/v1/categories/slug/{slug}", "lighting"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.slug").value("lighting"));
    }

    @Test
    @DisplayName("GET /categories/slug/{slug} returns 404 when slug not found")
    void getCategoryBySlugNotFound() throws Exception {
        when(categoryService.getCategoryBySlug("unknown"))
                .thenThrow(new ResourceNotFoundException("Category with slug unknown not found"));

        mockMvc.perform(get("/api/v1/categories/slug/{slug}", "unknown"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Category with slug unknown not found"));
    }
}
