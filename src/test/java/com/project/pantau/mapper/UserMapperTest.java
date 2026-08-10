package com.project.pantau.mapper;

import com.project.pantau.dto.auth.UserResponse;
import com.project.pantau.entity.User;
import com.project.pantau.enums.UserRole;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class UserMapperTest {

    private final UserMapper userMapper = Mappers.getMapper(UserMapper.class);

    @Test
    void toResponse_mapsAllFieldsFromUser() {
        UUID id = UUID.randomUUID();
        LocalDateTime createdAt = LocalDateTime.of(2026, 1, 1, 10, 0);
        LocalDateTime updatedAt = LocalDateTime.of(2026, 2, 1, 12, 30);

        User user = User.builder()
                .id(id)
                .email("citizen@example.com")
                .password("super-secret")
                .displayName("Citizen Jane")
                .role(UserRole.CITIZEN)
                .createdAt(createdAt)
                .updatedAt(updatedAt)
                .build();

        UserResponse response = userMapper.toResponse(user);

        assertThat(response).isNotNull();
        assertThat(response.id()).isEqualTo(id);
        assertThat(response.email()).isEqualTo("citizen@example.com");
        assertThat(response.displayName()).isEqualTo("Citizen Jane");
        assertThat(response.role()).isEqualTo(UserRole.CITIZEN);
        assertThat(response.createdAt()).isEqualTo(createdAt);
        assertThat(response.updatedAt()).isEqualTo(updatedAt);
    }

    @Test
    void toResponse_mapsResolverRole() {
        User user = User.builder()
                .id(UUID.randomUUID())
                .email("resolver@example.com")
                .password("secret")
                .displayName("Resolver Bob")
                .role(UserRole.RESOLVER)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        UserResponse response = userMapper.toResponse(user);

        assertThat(response.role()).isEqualTo(UserRole.RESOLVER);
    }

    @Test
    void toResponse_returnsNullForNullInput() {
        assertThat(userMapper.toResponse(null)).isNull();
    }
}
