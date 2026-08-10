package com.project.pantau.service.impl;

import com.project.pantau.common.config.JwtProperties;
import com.project.pantau.common.exception.EmailAlreadyExistsException;
import com.project.pantau.common.exception.ResourceNotFoundException;
import com.project.pantau.common.security.CustomUserDetails;
import com.project.pantau.common.security.JwtService;
import com.project.pantau.dto.auth.AuthResponse;
import com.project.pantau.dto.auth.LoginRequest;
import com.project.pantau.dto.auth.RegisterRequest;
import com.project.pantau.dto.auth.UserResponse;
import com.project.pantau.entity.User;
import com.project.pantau.enums.UserRole;
import com.project.pantau.mapper.AuthMapper;
import com.project.pantau.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceImplTest {

    private final UUID userId = UUID.randomUUID();
    @Mock
    private UserRepository userRepository;
    @Mock
    private JwtService jwtService;
    @Mock
    private JwtProperties jwtProperties;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private AuthenticationManager authenticationManager;
    @Mock
    private AuthMapper authMapper;
    private AuthServiceImpl authService;

    @org.junit.jupiter.api.BeforeEach
    void setUp() {
        authService = new AuthServiceImpl(
                userRepository,
                jwtService,
                jwtProperties,
                passwordEncoder,
                authenticationManager,
                authMapper
        );
    }

    private User buildUser() {
        return User.builder()
                .id(userId)
                .email("jane@example.com")
                .password("encoded-password")
                .displayName("Jane")
                .role(UserRole.CITIZEN)
                .createdAt(LocalDateTime.of(2026, 1, 1, 0, 0))
                .updatedAt(LocalDateTime.of(2026, 1, 1, 0, 0))
                .build();
    }

    private UserResponse buildUserResponse(User user) {
        return UserResponse.builder()
                .id(user.getId())
                .email(user.getEmail())
                .displayName(user.getDisplayName())
                .role(user.getRole())
                .createdAt(user.getCreatedAt())
                .updatedAt(user.getUpdatedAt())
                .build();
    }

    @Test
    @DisplayName("register encodes password, saves user, and returns mapped auth response")
    void registerSuccess() {
        var request = new RegisterRequest("jane@example.com", "Jane", "password123");
        var savedUser = buildUser();
        var userResponse = buildUserResponse(savedUser);
        var expectedResponse = AuthResponse.builder()
                .token("jwt-token")
                .expiresIn(3600L)
                .userResponse(userResponse)
                .build();

        when(userRepository.existsByEmail("jane@example.com")).thenReturn(false);
        when(passwordEncoder.encode("password123")).thenReturn("encoded-password");
        when(userRepository.saveAndFlush(any(User.class))).thenReturn(savedUser);
        when(jwtService.generateToken(any(), any())).thenReturn("jwt-token");
        when(jwtProperties.expiration()).thenReturn(3600L);
        when(authMapper.toResponse(eq("jwt-token"), eq(3600L), eq(savedUser))).thenReturn(expectedResponse);

        var result = authService.register(request);

        assertThat(result).isEqualTo(expectedResponse);
        assertThat(result.token()).isEqualTo("jwt-token");
        assertThat(result.expiresIn()).isEqualTo(3600L);
        assertThat(result.userResponse()).isEqualTo(userResponse);

        var userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).saveAndFlush(userCaptor.capture());
        var savedArg = userCaptor.getValue();
        assertThat(savedArg.getEmail()).isEqualTo("jane@example.com");
        assertThat(savedArg.getDisplayName()).isEqualTo("Jane");
        assertThat(savedArg.getPassword()).isEqualTo("encoded-password");
        assertThat(savedArg.getRole()).isEqualTo(UserRole.CITIZEN);

        verify(passwordEncoder).encode("password123");
        verify(userRepository).existsByEmail("jane@example.com");
    }

    @Test
    @DisplayName("register throws EmailAlreadyExistsException when email already exists and never saves")
    void registerDuplicateEmail() {
        var request = new RegisterRequest("jane@example.com", "Jane", "password123");
        when(userRepository.existsByEmail("jane@example.com")).thenReturn(true);

        assertThatThrownBy(() -> authService.register(request))
                .isInstanceOf(EmailAlreadyExistsException.class)
                .hasMessageContaining("already exists");

        verify(userRepository, never()).saveAndFlush(any());
        verify(passwordEncoder, never()).encode(anyString());
    }

    @Test
    @DisplayName("login authenticates credentials and returns mapped auth response")
    void loginSuccess() {
        var request = new LoginRequest("jane@example.com", "password123");
        var user = buildUser();
        var userResponse = buildUserResponse(user);
        var expectedResponse = AuthResponse.builder()
                .token("jwt-token")
                .expiresIn(3600L)
                .userResponse(userResponse)
                .build();

        when(userRepository.findByEmail("jane@example.com")).thenReturn(Optional.of(user));
        when(jwtService.generateToken(any(), any())).thenReturn("jwt-token");
        when(jwtProperties.expiration()).thenReturn(3600L);
        when(authMapper.toResponse(eq("jwt-token"), eq(3600L), eq(user))).thenReturn(expectedResponse);

        var result = authService.login(request);

        assertThat(result).isEqualTo(expectedResponse);

        var tokenCaptor = ArgumentCaptor.forClass(UsernamePasswordAuthenticationToken.class);
        verify(authenticationManager).authenticate(tokenCaptor.capture());
        assertThat(tokenCaptor.getValue().getPrincipal()).isEqualTo("jane@example.com");
        assertThat(tokenCaptor.getValue().getCredentials()).isEqualTo("password123");

        verify(userRepository).findByEmail("jane@example.com");
    }

    @Test
    @DisplayName("login throws ResourceNotFoundException when authenticated user cannot be found by email")
    void loginUserNotFoundAfterAuthentication() {
        var request = new LoginRequest("ghost@example.com", "password123");
        when(userRepository.findByEmail("ghost@example.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.login(request))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("ghost@example.com");

        verify(authenticationManager, times(1)).authenticate(any());
        verify(jwtService, never()).generateToken(any(), any());
    }

    @Test
    @DisplayName("login propagates authentication failure and never queries the repository")
    void loginAuthenticationFailurePropagates() {
        var request = new LoginRequest("jane@example.com", "wrong-password");
        when(authenticationManager.authenticate(any()))
                .thenThrow(new BadCredentialsException("Bad credentials"));

        assertThatThrownBy(() -> authService.login(request))
                .isInstanceOf(BadCredentialsException.class);

        verify(userRepository, never()).findByEmail(anyString());
        verify(jwtService, never()).generateToken(any(), any());
    }

    @Test
    @DisplayName("buildAuthResponse maps role claim via CustomUserDetails authorities")
    void registerBuildsClaimsWithRoleAuthority() {
        var request = new RegisterRequest("jane@example.com", "Jane", "password123");
        var savedUser = buildUser();

        when(userRepository.existsByEmail("jane@example.com")).thenReturn(false);
        when(passwordEncoder.encode("password123")).thenReturn("encoded-password");
        when(userRepository.saveAndFlush(any(User.class))).thenReturn(savedUser);
        when(jwtService.generateToken(any(), any())).thenReturn("jwt-token");
        when(jwtProperties.expiration()).thenReturn(3600L);
        when(authMapper.toResponse(any(), any(), any())).thenReturn(
                AuthResponse.builder().token("jwt-token").expiresIn(3600L).build()
        );

        authService.register(request);

        var userDetailsCaptor = ArgumentCaptor.forClass(CustomUserDetails.class);
        verify(jwtService).generateToken(any(), userDetailsCaptor.capture());
        assertThat(userDetailsCaptor.getValue().getUsername()).isEqualTo("jane@example.com");
        assertThat(userDetailsCaptor.getValue().getAuthorities())
                .extracting(a -> a.getAuthority())
                .containsExactly("ROLE_CITIZEN");
    }
}
