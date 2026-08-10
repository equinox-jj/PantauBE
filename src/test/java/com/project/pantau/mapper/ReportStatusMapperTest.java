package com.project.pantau.mapper;

import com.project.pantau.dto.report_status.ReportStatusResponse;
import com.project.pantau.entity.Report;
import com.project.pantau.entity.ReportStatusHistory;
import com.project.pantau.entity.User;
import com.project.pantau.enums.ReportStatus;
import com.project.pantau.enums.UserRole;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ReportStatusMapperTest {

    private final ReportStatusMapper reportStatusMapper = Mappers.getMapper(ReportStatusMapper.class);

    private User actor(UserRole role) {
        return User.builder()
                .id(UUID.randomUUID())
                .email("actor@example.com")
                .password("secret")
                .displayName("Actor")
                .role(role)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
    }

    @Test
    void toResponse_mapsFieldsAndActorRoleFromNestedActor() {
        UUID id = UUID.randomUUID();
        LocalDateTime createdAt = LocalDateTime.of(2026, 3, 1, 9, 15);
        User actor = actor(UserRole.RESOLVER);

        ReportStatusHistory entity = ReportStatusHistory.builder()
                .id(id)
                .report(Report.builder().id(UUID.randomUUID()).build())
                .actor(actor)
                .fromStatus(ReportStatus.REPORTED)
                .toStatus(ReportStatus.ACKNOWLEDGED)
                .note("Acknowledged by resolver")
                .createdAt(createdAt)
                .build();

        ReportStatusResponse response = reportStatusMapper.toResponse(entity);

        assertThat(response).isNotNull();
        assertThat(response.id()).isEqualTo(id);
        assertThat(response.fromStatus()).isEqualTo(ReportStatus.REPORTED);
        assertThat(response.toStatus()).isEqualTo(ReportStatus.ACKNOWLEDGED);
        assertThat(response.note()).isEqualTo("Acknowledged by resolver");
        assertThat(response.actorRole()).isEqualTo(UserRole.RESOLVER);
        assertThat(response.createdAt()).isEqualTo(createdAt);
    }

    @Test
    void toResponse_mapsNullFromStatusForInitialReportCreation() {
        ReportStatusHistory entity = ReportStatusHistory.builder()
                .id(UUID.randomUUID())
                .report(Report.builder().id(UUID.randomUUID()).build())
                .actor(actor(UserRole.CITIZEN))
                .fromStatus(null)
                .toStatus(ReportStatus.REPORTED)
                .note(null)
                .createdAt(LocalDateTime.now())
                .build();

        ReportStatusResponse response = reportStatusMapper.toResponse(entity);

        assertThat(response.fromStatus()).isNull();
        assertThat(response.note()).isNull();
        assertThat(response.actorRole()).isEqualTo(UserRole.CITIZEN);
    }

    @Test
    void toResponse_returnsNullForNullInput() {
        assertThat(reportStatusMapper.toResponse((ReportStatusHistory) null)).isNull();
    }

    @Test
    void toResponseList_mapsEachElementInOrder() {
        ReportStatusHistory first = ReportStatusHistory.builder()
                .id(UUID.randomUUID())
                .report(Report.builder().id(UUID.randomUUID()).build())
                .actor(actor(UserRole.CITIZEN))
                .fromStatus(null)
                .toStatus(ReportStatus.REPORTED)
                .createdAt(LocalDateTime.now())
                .build();
        ReportStatusHistory second = ReportStatusHistory.builder()
                .id(UUID.randomUUID())
                .report(Report.builder().id(UUID.randomUUID()).build())
                .actor(actor(UserRole.RESOLVER))
                .fromStatus(ReportStatus.REPORTED)
                .toStatus(ReportStatus.RESOLVED)
                .createdAt(LocalDateTime.now())
                .build();

        List<ReportStatusResponse> responses = reportStatusMapper.toResponse(List.of(first, second));

        assertThat(responses).hasSize(2);
        assertThat(responses.get(0).toStatus()).isEqualTo(ReportStatus.REPORTED);
        assertThat(responses.get(0).actorRole()).isEqualTo(UserRole.CITIZEN);
        assertThat(responses.get(1).toStatus()).isEqualTo(ReportStatus.RESOLVED);
        assertThat(responses.get(1).actorRole()).isEqualTo(UserRole.RESOLVER);
    }

    @Test
    void toResponseList_returnsNullForNullInput() {
        assertThat(reportStatusMapper.toResponse((List<ReportStatusHistory>) null)).isNull();
    }
}
