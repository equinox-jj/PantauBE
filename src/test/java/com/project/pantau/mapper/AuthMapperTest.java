package com.project.pantau.mapper;

import com.project.pantau.dto.auth.AuthResponse;
import com.project.pantau.entity.User;
import com.project.pantau.enums.UserRole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * AuthMapper uses UserMapper (uses = UserMapper.class) which is injected via
 * Spring (@Autowired field) in the generated component-model=spring impl.
 * Mappers.getMapper() only performs a no-arg instantiation, so the delegate
 * mapper is wired in manually via reflection to mirror what Spring would do.
 */
class AuthMapperTest {

    private AuthMapper authMapper;

    @BeforeEach
    void setUp() {
        AuthMapperImpl impl = new AuthMapperImpl();
        ReflectionTestUtils.setField(impl, "userMapper", new UserMapperImpl());
        authMapper = impl;
    }

    @Test
    void toResponse_mapsTokenExpiresInAndDelegatesUserToUserResponse() {
        UUID userId = UUID.randomUUID();
        LocalDateTime createdAt = LocalDateTime.of(2026, 1, 5, 8, 0);
        LocalDateTime updatedAt = LocalDateTime.of(2026, 1, 6, 9, 0);

        User user = User.builder()
                .id(userId)
                .email("user@example.com")
                .password("secret")
                .displayName("Some User")
                .role(UserRole.CITIZEN)
                .createdAt(createdAt)
                .updatedAt(updatedAt)
                .build();

        AuthResponse response = authMapper.toResponse("jwt-token", 3600L, user);

        assertThat(response).isNotNull();
        assertThat(response.token()).isEqualTo("jwt-token");
        assertThat(response.expiresIn()).isEqualTo(3600L);
        assertThat(response.userResponse()).isNotNull();
        assertThat(response.userResponse().id()).isEqualTo(userId);
        assertThat(response.userResponse().email()).isEqualTo("user@example.com");
        assertThat(response.userResponse().displayName()).isEqualTo("Some User");
        assertThat(response.userResponse().role()).isEqualTo(UserRole.CITIZEN);
        assertThat(response.userResponse().createdAt()).isEqualTo(createdAt);
        assertThat(response.userResponse().updatedAt()).isEqualTo(updatedAt);
    }

    @Test
    void toResponse_mapsNullUserToNullUserResponse() {
        AuthResponse response = authMapper.toResponse("jwt-token", 3600L, null);

        assertThat(response).isNotNull();
        assertThat(response.token()).isEqualTo("jwt-token");
        assertThat(response.expiresIn()).isEqualTo(3600L);
        assertThat(response.userResponse()).isNull();
    }

    @Test
    void toResponse_returnsNullWhenAllArgumentsAreNull() {
        AuthResponse response = authMapper.toResponse(null, null, null);

        assertThat(response).isNull();
    }
}
