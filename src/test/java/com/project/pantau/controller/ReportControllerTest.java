package com.project.pantau.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.project.pantau.common.exception.GlobalExceptionHandler;
import com.project.pantau.common.exception.IllegalTransitionException;
import com.project.pantau.common.exception.ResourceNotFoundException;
import com.project.pantau.common.response.PageMeta;
import com.project.pantau.common.response.PagedResponse;
import com.project.pantau.common.security.CustomUserDetails;
import com.project.pantau.dto.category.CategoryResponse;
import com.project.pantau.dto.report.*;
import com.project.pantau.dto.report_status.ReportStatusResponse;
import com.project.pantau.entity.User;
import com.project.pantau.enums.QueueTab;
import com.project.pantau.enums.ReportStatus;
import com.project.pantau.enums.UserRole;
import com.project.pantau.service.ReportService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class ReportControllerTest {

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
    @Mock
    private ReportService reportService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        var controller = new ReportController(reportService);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setValidator(new LocalValidatorFactoryBean())
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

    private User authenticateAs() {
        var user = buildUser();
        var principal = new CustomUserDetails(user);
        var authentication = new UsernamePasswordAuthenticationToken(
                principal, null, principal.getAuthorities());
        SecurityContextHolder.getContext().setAuthentication(authentication);
        return user;
    }

    private CategoryResponse buildCategory() {
        return CategoryResponse.builder()
                .id(1L)
                .name("Roads")
                .slug("roads")
                .isActive(true)
                .build();
    }

    private ReportResponse buildReportResponse(UUID id) {
        return ReportResponse.builder()
                .id(id)
                .category(buildCategory())
                .description("Pothole on Main St")
                .photoUrl("https://cdn.example.com/photo.jpg")
                .latitude(-6.2)
                .longitude(106.8)
                .status(ReportStatus.REPORTED)
                .createdAt(LocalDateTime.of(2026, 1, 1, 0, 0))
                .updatedAt(LocalDateTime.of(2026, 1, 1, 0, 0))
                .build();
    }

    // ---------- createReport ----------

    @Test
    @DisplayName("POST /reports returns 201 with created report")
    void createReportSuccess() throws Exception {
        var user = authenticateAs();
        var id = UUID.randomUUID();
        var response = buildReportResponse(id);
        var photo = new MockMultipartFile("photo", "photo.jpg", MediaType.IMAGE_JPEG_VALUE, "image-bytes".getBytes());

        when(reportService.createReport(eq(user), any(CreateReportRequest.class))).thenReturn(response);

        mockMvc.perform(multipart("/api/v1/reports")
                        .file(photo)
                        .param("categoryId", "1")
                        .param("description", "Pothole on Main St")
                        .param("latitude", "-6.2")
                        .param("longitude", "106.8"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value(true))
                .andExpect(jsonPath("$.message").value("Successfully created report"))
                .andExpect(jsonPath("$.data.id").value(id.toString()))
                .andExpect(jsonPath("$.data.status").value("REPORTED"))
                .andExpect(jsonPath("$.data.category.slug").value("roads"));

        verify(reportService).createReport(eq(user), any(CreateReportRequest.class));
    }

    @Test
    @DisplayName("POST /reports returns 422 when categoryId is missing")
    void createReportValidationFailureMissingCategory() throws Exception {
        authenticateAs();
        var photo = new MockMultipartFile("photo", "photo.jpg", MediaType.IMAGE_JPEG_VALUE, "image-bytes".getBytes());

        mockMvc.perform(multipart("/api/v1/reports")
                        .file(photo)
                        .param("latitude", "-6.2")
                        .param("longitude", "106.8"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.status").value(false))
                .andExpect(jsonPath("$.message").value("Validation failed"));
    }

    @Test
    @DisplayName("POST /reports returns 422 when photo is missing")
    void createReportValidationFailureMissingPhoto() throws Exception {
        authenticateAs();

        mockMvc.perform(multipart("/api/v1/reports")
                        .param("categoryId", "1")
                        .param("latitude", "-6.2")
                        .param("longitude", "106.8"))
                .andExpect(status().isUnprocessableEntity());
    }

    // ---------- getReportDetail ----------

    @Test
    @DisplayName("GET /reports/{id} returns 200 with report detail")
    void getReportDetailSuccess() throws Exception {
        var id = UUID.randomUUID();
        var response = buildReportResponse(id);
        when(reportService.getReportDetail(id)).thenReturn(response);

        mockMvc.perform(get("/api/v1/reports/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(true))
                .andExpect(jsonPath("$.message").value("Successfully retrieved report"))
                .andExpect(jsonPath("$.data.id").value(id.toString()));
    }

    @Test
    @DisplayName("GET /reports/{id} returns 404 when report not found")
    void getReportDetailNotFound() throws Exception {
        var id = UUID.randomUUID();
        when(reportService.getReportDetail(id))
                .thenThrow(new ResourceNotFoundException("Report not found"));

        mockMvc.perform(get("/api/v1/reports/{id}", id))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(false))
                .andExpect(jsonPath("$.message").value("Report not found"));
    }

    @Test
    @DisplayName("GET /reports/{id} returns 400 when id is not a valid UUID")
    void getReportDetailInvalidId() throws Exception {
        mockMvc.perform(get("/api/v1/reports/{id}", "not-a-uuid"))
                .andExpect(status().isBadRequest());
    }

    // ---------- getReportHistory ----------

    @Test
    @DisplayName("GET /reports/{id}/history returns 200 with status history")
    void getReportHistorySuccess() throws Exception {
        var id = UUID.randomUUID();
        var history = List.of(
                ReportStatusResponse.builder()
                        .id(UUID.randomUUID())
                        .fromStatus(null)
                        .toStatus(ReportStatus.REPORTED)
                        .note("Initial report")
                        .actorRole(UserRole.CITIZEN)
                        .createdAt(LocalDateTime.of(2026, 1, 1, 0, 0))
                        .build(),
                ReportStatusResponse.builder()
                        .id(UUID.randomUUID())
                        .fromStatus(ReportStatus.REPORTED)
                        .toStatus(ReportStatus.ACKNOWLEDGED)
                        .note("Acknowledged")
                        .actorRole(UserRole.RESOLVER)
                        .createdAt(LocalDateTime.of(2026, 1, 2, 0, 0))
                        .build()
        );
        when(reportService.getReportHistory(id)).thenReturn(history);

        mockMvc.perform(get("/api/v1/reports/{id}/history", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Successfully retrieved report history"))
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[1].toStatus").value("ACKNOWLEDGED"));
    }

    @Test
    @DisplayName("GET /reports/{id}/history returns 404 when report not found")
    void getReportHistoryNotFound() throws Exception {
        var id = UUID.randomUUID();
        when(reportService.getReportHistory(id))
                .thenThrow(new ResourceNotFoundException("Report not found"));

        mockMvc.perform(get("/api/v1/reports/{id}/history", id))
                .andExpect(status().isNotFound());
    }

    // ---------- getNearbyReports ----------

    @Test
    @DisplayName("GET /reports/nearby returns 200 with nearby reports")
    void getNearbyReportsSuccess() throws Exception {
        var nearby = List.of(
                NearbyReportResponse.builder()
                        .id(UUID.randomUUID())
                        .category(buildCategory())
                        .photoUrl("https://cdn.example.com/photo.jpg")
                        .status(ReportStatus.REPORTED)
                        .latitude(-6.2)
                        .longitude(106.8)
                        .createdAt(LocalDateTime.of(2026, 1, 1, 0, 0))
                        .build()
        );
        when(reportService.getNearbyReports(-6.2, 106.8, 1000, 20)).thenReturn(nearby);

        mockMvc.perform(get("/api/v1/reports/nearby")
                        .param("latitude", "-6.2")
                        .param("longitude", "106.8"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Successfully retrieved nearby reports"))
                .andExpect(jsonPath("$.data.length()").value(1));

        verify(reportService).getNearbyReports(-6.2, 106.8, 1000, 20);
    }

    @Test
    @DisplayName("GET /reports/nearby honors custom radius and limit query params")
    void getNearbyReportsCustomParams() throws Exception {
        when(reportService.getNearbyReports(-6.2, 106.8, 500, 5)).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/reports/nearby")
                        .param("latitude", "-6.2")
                        .param("longitude", "106.8")
                        .param("radius_meter", "500")
                        .param("limit", "5"))
                .andExpect(status().isOk());

        verify(reportService).getNearbyReports(-6.2, 106.8, 500, 5);
    }

    @Test
    @DisplayName("GET /reports/nearby returns 400 when latitude param is missing")
    void getNearbyReportsMissingLatitude() throws Exception {
        mockMvc.perform(get("/api/v1/reports/nearby")
                        .param("longitude", "106.8"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Required parameter 'latitude' is missing"));
    }

    // ---------- getMyReports ----------

    @Test
    @DisplayName("GET /reports/mine returns 200 with paged reports for the authenticated user")
    void getMyReportsSuccess() throws Exception {
        var user = authenticateAs();
        var id = UUID.randomUUID();
        var paged = new PagedResponse<>(List.of(buildReportResponse(id)), new PageMeta(20, 0, 1, false));
        when(reportService.getMyReports(user, 20, 0)).thenReturn(paged);

        mockMvc.perform(get("/api/v1/reports/mine"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Successfully retrieved my reports"))
                .andExpect(jsonPath("$.data.items.length()").value(1))
                .andExpect(jsonPath("$.data.meta.total").value(1))
                .andExpect(jsonPath("$.data.meta.hasNext").value(false));

        verify(reportService).getMyReports(user, 20, 0);
    }

    @Test
    @DisplayName("GET /reports/mine honors custom limit and offset query params")
    void getMyReportsCustomParams() throws Exception {
        var user = authenticateAs();
        var paged = new PagedResponse<ReportResponse>(List.of(), new PageMeta(5, 10, 0, false));
        when(reportService.getMyReports(user, 5, 10)).thenReturn(paged);

        mockMvc.perform(get("/api/v1/reports/mine")
                        .param("limit", "5")
                        .param("offset", "10"))
                .andExpect(status().isOk());

        verify(reportService).getMyReports(user, 5, 10);
    }

    // ---------- updateReport ----------

    @Test
    @DisplayName("PATCH /reports/{id} returns 200 with updated report")
    void updateReportSuccess() throws Exception {
        var user = authenticateAs();
        var id = UUID.randomUUID();
        var response = buildReportResponse(id);
        var photo = new MockMultipartFile("photo", "photo.jpg", MediaType.IMAGE_JPEG_VALUE, "image-bytes".getBytes());

        when(reportService.updateReport(eq(id), eq(user), any())).thenReturn(response);

        mockMvc.perform(multipart(HttpMethod.PATCH, "/api/v1/reports/{id}", id)
                        .file(photo)
                        .param("categoryId", "1")
                        .param("description", "Updated description")
                        .param("latitude", "-6.2")
                        .param("longitude", "106.8"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(true))
                .andExpect(jsonPath("$.message").value("Successfully updated report"))
                .andExpect(jsonPath("$.data.id").value(id.toString()));

        verify(reportService).updateReport(eq(id), eq(user), any());
    }

    @Test
    @DisplayName("PATCH /reports/{id} succeeds without a new photo since photo is optional")
    void updateReportSuccessWithoutPhoto() throws Exception {
        var user = authenticateAs();
        var id = UUID.randomUUID();
        var response = buildReportResponse(id);

        when(reportService.updateReport(eq(id), eq(user), any())).thenReturn(response);

        mockMvc.perform(multipart(HttpMethod.PATCH, "/api/v1/reports/{id}", id)
                        .param("categoryId", "1")
                        .param("latitude", "-6.2")
                        .param("longitude", "106.8"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("PATCH /reports/{id} returns 422 when latitude is missing")
    void updateReportValidationFailure() throws Exception {
        authenticateAs();
        var id = UUID.randomUUID();

        mockMvc.perform(multipart(HttpMethod.PATCH, "/api/v1/reports/{id}", id)
                        .param("categoryId", "1")
                        .param("longitude", "106.8"))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    @DisplayName("PATCH /reports/{id} returns 404 when report does not exist")
    void updateReportNotFound() throws Exception {
        var user = authenticateAs();
        var id = UUID.randomUUID();

        when(reportService.updateReport(eq(id), eq(user), any()))
                .thenThrow(new ResourceNotFoundException("Report not found"));

        mockMvc.perform(multipart(HttpMethod.PATCH, "/api/v1/reports/{id}", id)
                        .param("categoryId", "1")
                        .param("latitude", "-6.2")
                        .param("longitude", "106.8"))
                .andExpect(status().isNotFound());
    }

    // ---------- deleteReport ----------

    @Test
    @DisplayName("DELETE /reports/{id} returns 200 with success message")
    void deleteReportSuccess() throws Exception {
        var user = authenticateAs();
        var id = UUID.randomUUID();

        mockMvc.perform(delete("/api/v1/reports/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(true))
                .andExpect(jsonPath("$.message").value("Successfully deleted report"))
                .andExpect(jsonPath("$.data").doesNotExist());

        verify(reportService).deleteReport(id, user);
    }

    @Test
    @DisplayName("DELETE /reports/{id} returns 404 when report does not exist")
    void deleteReportNotFound() throws Exception {
        var user = authenticateAs();
        var id = UUID.randomUUID();

        org.mockito.Mockito.doThrow(new ResourceNotFoundException("Report not found"))
                .when(reportService).deleteReport(id, user);

        mockMvc.perform(delete("/api/v1/reports/{id}", id))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Report not found"));
    }

    // ---------- getQueue ----------

    @Test
    @DisplayName("GET /reports/queue returns 200 with the resolver queue")
    void getQueueSuccess() throws Exception {
        var queueReport = QueueReportResponse.builder()
                .id(UUID.randomUUID())
                .category(buildCategory())
                .description("Pothole")
                .photoUrl("https://cdn.example.com/photo.jpg")
                .status(ReportStatus.REPORTED)
                .latitude(-6.2)
                .longitude(106.8)
                .distanceMeter(120.5)
                .createdAt(LocalDateTime.of(2026, 1, 1, 0, 0))
                .build();
        var counts = QueueCounts.builder().open(3).inProgress(1).resolved(2).build();
        var response = QueueResponse.builder()
                .items(List.of(queueReport))
                .meta(new PageMeta(20, 0, 1, false))
                .counts(counts)
                .build();

        when(reportService.getQueue(QueueTab.OPEN, -6.2, 106.8, 5000, 20, 0)).thenReturn(response);

        mockMvc.perform(get("/api/v1/reports/queue")
                        .param("tab", "OPEN")
                        .param("latitude", "-6.2")
                        .param("longitude", "106.8"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Successfully retrieved queue"))
                .andExpect(jsonPath("$.data.items.length()").value(1))
                .andExpect(jsonPath("$.data.counts.open").value(3))
                .andExpect(jsonPath("$.data.counts.inProgress").value(1))
                .andExpect(jsonPath("$.data.counts.resolved").value(2));

        verify(reportService).getQueue(QueueTab.OPEN, -6.2, 106.8, 5000, 20, 0);
    }

    @Test
    @DisplayName("GET /reports/queue honors custom radius, limit and offset")
    void getQueueCustomParams() throws Exception {
        var response = QueueResponse.builder()
                .items(List.of())
                .meta(new PageMeta(10, 5, 0, false))
                .counts(QueueCounts.builder().open(0).inProgress(0).resolved(0).build())
                .build();
        when(reportService.getQueue(QueueTab.IN_PROGRESS, -6.2, 106.8, 2000, 10, 5)).thenReturn(response);

        mockMvc.perform(get("/api/v1/reports/queue")
                        .param("tab", "IN_PROGRESS")
                        .param("latitude", "-6.2")
                        .param("longitude", "106.8")
                        .param("radius_meter", "2000")
                        .param("limit", "10")
                        .param("offset", "5"))
                .andExpect(status().isOk());

        verify(reportService).getQueue(QueueTab.IN_PROGRESS, -6.2, 106.8, 2000, 10, 5);
    }

    @Test
    @DisplayName("GET /reports/queue returns 400 when tab param is invalid")
    void getQueueInvalidTab() throws Exception {
        mockMvc.perform(get("/api/v1/reports/queue")
                        .param("tab", "INVALID_TAB")
                        .param("latitude", "-6.2")
                        .param("longitude", "106.8"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("GET /reports/queue returns 400 when tab param is missing")
    void getQueueMissingTab() throws Exception {
        mockMvc.perform(get("/api/v1/reports/queue")
                        .param("latitude", "-6.2")
                        .param("longitude", "106.8"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Required parameter 'tab' is missing"));
    }

    // ---------- updateReportStatus ----------

    @Test
    @DisplayName("PATCH /reports/{id}/status returns 200 with updated report")
    void updateReportStatusSuccess() throws Exception {
        var user = authenticateAs();
        var id = UUID.randomUUID();
        var request = new UpdateStatusRequest(ReportStatus.ACKNOWLEDGED, "Looking into it");
        var response = buildReportResponse(id);

        when(reportService.updateReportStatus(eq(id), eq(user), any(UpdateStatusRequest.class)))
                .thenReturn(response);

        mockMvc.perform(patch("/api/v1/reports/{id}/status", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(true))
                .andExpect(jsonPath("$.message").value("Successfully updated report status"))
                .andExpect(jsonPath("$.data.id").value(id.toString()));

        verify(reportService).updateReportStatus(eq(id), eq(user), any(UpdateStatusRequest.class));
    }

    @Test
    @DisplayName("PATCH /reports/{id}/status returns 422 when toStatus is missing")
    void updateReportStatusValidationFailure() throws Exception {
        authenticateAs();
        var id = UUID.randomUUID();
        var request = new UpdateStatusRequest(null, "note");

        mockMvc.perform(patch("/api/v1/reports/{id}/status", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.message").value("Validation failed"));
    }

    @Test
    @DisplayName("PATCH /reports/{id}/status returns 409 when the status transition is illegal")
    void updateReportStatusIllegalTransition() throws Exception {
        var user = authenticateAs();
        var id = UUID.randomUUID();
        var request = new UpdateStatusRequest(ReportStatus.RESOLVED, null);

        when(reportService.updateReportStatus(eq(id), eq(user), any(UpdateStatusRequest.class)))
                .thenThrow(new IllegalTransitionException("Cannot transition from REPORTED to RESOLVED"));

        mockMvc.perform(patch("/api/v1/reports/{id}/status", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Cannot transition from REPORTED to RESOLVED"));
    }

    @Test
    @DisplayName("PATCH /reports/{id}/status returns 404 when report does not exist")
    void updateReportStatusNotFound() throws Exception {
        var user = authenticateAs();
        var id = UUID.randomUUID();
        var request = new UpdateStatusRequest(ReportStatus.ACKNOWLEDGED, null);

        when(reportService.updateReportStatus(eq(id), eq(user), any(UpdateStatusRequest.class)))
                .thenThrow(new ResourceNotFoundException("Report not found"));

        mockMvc.perform(patch("/api/v1/reports/{id}/status", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound());
    }
}
