package com.project.pantau.service.impl;

import com.project.pantau.entity.User;
import com.project.pantau.enums.ReportStatus;
import com.project.pantau.enums.UserRole;
import com.project.pantau.repository.ReportRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserServiceImplTest {

    @Mock
    private ReportRepository reportRepository;

    private UserServiceImpl userService;

    @BeforeEach
    void setUp() {
        userService = new UserServiceImpl(reportRepository);
    }

    private User buildUser() {
        var joinedAt = LocalDateTime.of(2025, 6, 15, 10, 30);
        return User.builder()
                .id(UUID.randomUUID())
                .email("jane@example.com")
                .password("secret")
                .displayName("Jane Doe")
                .role(UserRole.CITIZEN)
                .createdAt(joinedAt)
                .updatedAt(joinedAt)
                .build();
    }

    @Test
    @DisplayName("getProfile returns profile with report counts from repository")
    void getProfileReturnsAggregatedCounts() {
        var user = buildUser();

        when(reportRepository.countByReporterId(user.getId())).thenReturn(5L);
        when(reportRepository.countByReporterIdAndStatus(user.getId(), ReportStatus.RESOLVED)).thenReturn(3L);

        var result = userService.getProfile(user);

        assertThat(result.id()).isEqualTo(user.getId());
        assertThat(result.displayName()).isEqualTo("Jane Doe");
        assertThat(result.joinedAt()).isEqualTo(user.getCreatedAt());
        assertThat(result.reportsCount()).isEqualTo(5L);
        assertThat(result.resolvedCount()).isEqualTo(3L);
    }

    @Test
    @DisplayName("getProfile returns zero counts when user has no reports")
    void getProfileReturnsZeroCountsWhenNoReports() {
        var user = buildUser();

        when(reportRepository.countByReporterId(user.getId())).thenReturn(0L);
        when(reportRepository.countByReporterIdAndStatus(user.getId(), ReportStatus.RESOLVED)).thenReturn(0L);

        var result = userService.getProfile(user);

        assertThat(result.reportsCount()).isZero();
        assertThat(result.resolvedCount()).isZero();
        assertThat(result.id()).isEqualTo(user.getId());
        assertThat(result.displayName()).isEqualTo("Jane Doe");
    }
}
