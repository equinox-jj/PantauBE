package com.project.pantau.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.project.pantau.common.exception.EmailAlreadyExistsException;
import com.project.pantau.common.exception.GlobalExceptionHandler;
import com.project.pantau.dto.auth.AuthResponse;
import com.project.pantau.dto.auth.LoginRequest;
import com.project.pantau.dto.auth.RegisterRequest;
import com.project.pantau.dto.auth.UserResponse;
import com.project.pantau.enums.UserRole;
import com.project.pantau.service.AuthService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class AuthControllerTest {

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
    @Mock
    private AuthService authService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        var controller = new AuthController(authService);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setValidator(new LocalValidatorFactoryBean())
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    private UserResponse buildUserResponse() {
        return UserResponse.builder()
                .id(UUID.randomUUID())
                .email("citizen@example.com")
                .displayName("Citizen One")
                .role(UserRole.CITIZEN)
                .createdAt(LocalDateTime.of(2026, 1, 1, 0, 0))
                .updatedAt(LocalDateTime.of(2026, 1, 1, 0, 0))
                .build();
    }

    @Test
    @DisplayName("POST /register returns 201 with auth response on success")
    void registerSuccess() throws Exception {
        var request = new RegisterRequest("citizen@example.com", "Citizen One", "password123");
        var authResponse = AuthResponse.builder()
                .token("jwt-token")
                .expiresIn(3600L)
                .userResponse(buildUserResponse())
                .build();

        when(authService.register(any(RegisterRequest.class))).thenReturn(authResponse);

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value(true))
                .andExpect(jsonPath("$.message").value("Registration successful"))
                .andExpect(jsonPath("$.data.token").value("jwt-token"))
                .andExpect(jsonPath("$.data.expiresIn").value(3600))
                .andExpect(jsonPath("$.data.userResponse.email").value("citizen@example.com"))
                .andExpect(jsonPath("$.data.userResponse.role").value("CITIZEN"));

        verify(authService).register(any(RegisterRequest.class));
    }

    @Test
    @DisplayName("POST /register returns 422 when email is blank")
    void registerValidationFailureBlankEmail() throws Exception {
        var request = new RegisterRequest("", "Citizen One", "password123");

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.status").value(false))
                .andExpect(jsonPath("$.message").value("Validation failed"));
    }

    @Test
    @DisplayName("POST /register returns 422 when password is too short")
    void registerValidationFailureShortPassword() throws Exception {
        var request = new RegisterRequest("citizen@example.com", "Citizen One", "short");

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    @DisplayName("POST /register returns 409 when email already exists")
    void registerEmailAlreadyExists() throws Exception {
        var request = new RegisterRequest("citizen@example.com", "Citizen One", "password123");

        when(authService.register(any(RegisterRequest.class)))
                .thenThrow(new EmailAlreadyExistsException("Email already registered"));

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(false))
                .andExpect(jsonPath("$.message").value("Email already registered"));
    }

    @Test
    @DisplayName("POST /login returns 200 with auth response on success")
    void loginSuccess() throws Exception {
        var request = new LoginRequest("citizen@example.com", "password123");
        var authResponse = AuthResponse.builder()
                .token("jwt-token")
                .expiresIn(3600L)
                .userResponse(buildUserResponse())
                .build();

        when(authService.login(any(LoginRequest.class))).thenReturn(authResponse);

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(true))
                .andExpect(jsonPath("$.message").value("Login successful"))
                .andExpect(jsonPath("$.data.token").value("jwt-token"));

        verify(authService).login(any(LoginRequest.class));
    }

    @Test
    @DisplayName("POST /login returns 422 when password is blank")
    void loginValidationFailure() throws Exception {
        var request = new LoginRequest("citizen@example.com", "");

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    @DisplayName("POST /login returns 401 when credentials are invalid")
    void loginInvalidCredentials() throws Exception {
        var request = new LoginRequest("citizen@example.com", "wrongpassword");

        when(authService.login(any(LoginRequest.class)))
                .thenThrow(new BadCredentialsException("bad credentials"));

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Invalid email or password"));
    }
}
