package com.project.pantau.common.security;

import com.project.pantau.entity.User;
import com.project.pantau.enums.UserRole;
import com.project.pantau.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CustomUserDetailsServiceTest {

    @Mock
    private UserRepository userRepository;

    private CustomUserDetailsService customUserDetailsService;

    @org.junit.jupiter.api.BeforeEach
    void setUp() {
        customUserDetailsService = new CustomUserDetailsService(userRepository);
    }

    @Test
    @DisplayName("loadUserByUsername returns CustomUserDetails wrapping the found user")
    void loadUserByUsernameReturnsUserDetailsWhenFound() {
        User user = User.builder()
                .id(UUID.randomUUID())
                .email("test@example.com")
                .password("hashed-password")
                .displayName("Test User")
                .role(UserRole.CITIZEN)
                .build();
        when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(user));

        UserDetails result = customUserDetailsService.loadUserByUsername("test@example.com");

        assertThat(result).isInstanceOf(CustomUserDetails.class);
        assertThat(result.getUsername()).isEqualTo("test@example.com");
        assertThat(result.getPassword()).isEqualTo("hashed-password");
        assertThat(((CustomUserDetails) result).user()).isEqualTo(user);
    }

    @Test
    @DisplayName("loadUserByUsername throws UsernameNotFoundException when user not found")
    void loadUserByUsernameThrowsWhenNotFound() {
        when(userRepository.findByEmail("missing@example.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> customUserDetailsService.loadUserByUsername("missing@example.com"))
                .isInstanceOf(UsernameNotFoundException.class)
                .hasMessage("User not found: missing@example.com");
    }
}
