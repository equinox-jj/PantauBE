package com.project.pantau.common.security;

import com.project.pantau.entity.User;
import com.project.pantau.enums.UserRole;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.GrantedAuthority;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class CustomUserDetailsTest {

    private User buildUser(UserRole role) {
        return User.builder()
                .id(UUID.randomUUID())
                .email("test@example.com")
                .password("hashed-password")
                .displayName("Test User")
                .role(role)
                .build();
    }

    @Test
    @DisplayName("getAuthorities maps UserRole to a single ROLE_ prefixed authority")
    void getAuthoritiesMapsRole() {
        CustomUserDetails userDetails = CustomUserDetails.builder().user(buildUser(UserRole.CITIZEN)).build();

        assertThat(userDetails.getAuthorities())
                .extracting(GrantedAuthority::getAuthority)
                .containsExactly("ROLE_CITIZEN");
    }

    @Test
    @DisplayName("getAuthorities reflects RESOLVER role")
    void getAuthoritiesMapsResolverRole() {
        CustomUserDetails userDetails = CustomUserDetails.builder().user(buildUser(UserRole.RESOLVER)).build();

        assertThat(userDetails.getAuthorities())
                .extracting(GrantedAuthority::getAuthority)
                .containsExactly("ROLE_RESOLVER");
    }

    @Test
    @DisplayName("getUsername returns the user's email")
    void getUsernameReturnsEmail() {
        CustomUserDetails userDetails = CustomUserDetails.builder().user(buildUser(UserRole.CITIZEN)).build();

        assertThat(userDetails.getUsername()).isEqualTo("test@example.com");
    }

    @Test
    @DisplayName("getPassword returns the user's password")
    void getPasswordReturnsPassword() {
        CustomUserDetails userDetails = CustomUserDetails.builder().user(buildUser(UserRole.CITIZEN)).build();

        assertThat(userDetails.getPassword()).isEqualTo("hashed-password");
    }

    @Test
    @DisplayName("account status flags are all true")
    void accountStatusFlagsAreTrue() {
        CustomUserDetails userDetails = CustomUserDetails.builder().user(buildUser(UserRole.CITIZEN)).build();

        assertThat(userDetails.isAccountNonExpired()).isTrue();
        assertThat(userDetails.isAccountNonLocked()).isTrue();
        assertThat(userDetails.isCredentialsNonExpired()).isTrue();
        assertThat(userDetails.isEnabled()).isTrue();
    }

    @Test
    @DisplayName("user() accessor returns the wrapped entity")
    void userAccessorReturnsWrappedEntity() {
        User user = buildUser(UserRole.CITIZEN);
        CustomUserDetails userDetails = CustomUserDetails.builder().user(user).build();

        assertThat(userDetails.user()).isEqualTo(user);
    }
}
