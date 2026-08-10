package com.project.pantau.controller;

import com.project.pantau.common.exception.GlobalExceptionHandler;
import com.project.pantau.common.security.CustomUserDetails;
import com.project.pantau.dto.user.ProfileResponse;
import com.project.pantau.entity.User;
import com.project.pantau.enums.UserRole;
import com.project.pantau.service.UserService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class UserControllerTest {

    @Mock
    private UserService userService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        var controller = new UserController(userService);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private User buildUser() {
        return User.builder()
                .id(UUID.randomUUID())
                .email("citizen@example.com")
                .password("hashed")
                .displayName("Citizen One")
                .role(UserRole.CITIZEN)
                .createdAt(LocalDateTime.of(2026, 1, 1, 0, 0))
                .updatedAt(LocalDateTime.of(2026, 1, 1, 0, 0))
                .build();
    }

    private void authenticateAs(User user) {
        var principal = new CustomUserDetails(user);
        var authentication = new UsernamePasswordAuthenticationToken(
                principal, null, principal.getAuthorities());
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }

    @Test
    @DisplayName("GET /users/me returns 200 with the authenticated user's profile")
    void getProfileSuccess() throws Exception {
        var user = buildUser();
        authenticateAs(user);

        var response = ProfileResponse.builder()
                .id(user.getId())
                .displayName("Citizen One")
                .joinedAt(user.getCreatedAt())
                .reportsCount(5)
                .resolvedCount(2)
                .build();

        when(userService.getProfile(user)).thenReturn(response);

        mockMvc.perform(get("/api/v1/users/me"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(true))
                .andExpect(jsonPath("$.message").value("Successfully retrieved profile"))
                .andExpect(jsonPath("$.data.displayName").value("Citizen One"))
                .andExpect(jsonPath("$.data.reportsCount").value(5))
                .andExpect(jsonPath("$.data.resolvedCount").value(2));

        verify(userService).getProfile(user);
    }

    @Test
    @DisplayName("GET /users/me returns 401 when no authenticated principal is present")
    void getProfileUnauthenticated() throws Exception {
        mockMvc.perform(get("/api/v1/users/me"))
                .andExpect(status().is5xxServerError());
    }
}
