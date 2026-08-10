package com.project.pantau.service.impl;

import com.project.pantau.common.exception.IllegalTransitionException;
import com.project.pantau.common.exception.ResourceNotFoundException;
import com.project.pantau.common.exception.ValidationException;
import com.project.pantau.common.pagination.OffsetPageable;
import com.project.pantau.common.utils.GeoUtils;
import com.project.pantau.dto.category.CategoryResponse;
import com.project.pantau.dto.report.*;
import com.project.pantau.dto.report_status.ReportStatusResponse;
import com.project.pantau.dto.upload.UploadResponse;
import com.project.pantau.entity.Category;
import com.project.pantau.entity.Report;
import com.project.pantau.entity.ReportStatusHistory;
import com.project.pantau.entity.User;
import com.project.pantau.enums.QueueTab;
import com.project.pantau.enums.ReportStatus;
import com.project.pantau.enums.UserRole;
import com.project.pantau.mapper.ReportMapper;
import com.project.pantau.mapper.ReportStatusMapper;
import com.project.pantau.repository.CategoryRepository;
import com.project.pantau.repository.ReportRepository;
import com.project.pantau.repository.ReportStatusRepository;
import com.project.pantau.service.UploadService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ReportServiceImplTest {

    private static final double JAKARTA_LAT = -6.2088;
    private static final double JAKARTA_LNG = 106.8456;
    @Mock
    private ReportRepository reportRepository;
    @Mock
    private ReportStatusRepository reportStatusRepository;
    @Mock
    private CategoryRepository categoryRepository;
    @Mock
    private UploadService uploadService;
    @Mock
    private ReportMapper reportMapper;
    @Mock
    private ReportStatusMapper reportStatusMapper;
    private ReportServiceImpl reportService;

    @BeforeEach
    void setUp() {
        reportService = new ReportServiceImpl(
                reportRepository,
                reportStatusRepository,
                categoryRepository,
                uploadService,
                reportMapper,
                reportStatusMapper
        );
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private User buildUser(UUID id, UserRole role) {
        return User.builder()
                .id(id)
                .email("user-" + id + "@example.com")
                .password("encoded")
                .displayName("Test User")
                .role(role)
                .createdAt(LocalDateTime.of(2026, 1, 1, 0, 0))
                .updatedAt(LocalDateTime.of(2026, 1, 1, 0, 0))
                .build();
    }

    private Category buildCategory(Long id) {
        return Category.builder()
                .id(id)
                .name("Roads")
                .slug("roads")
                .isActive(true)
                .createdAt(LocalDateTime.of(2026, 1, 1, 0, 0))
                .build();
    }

    private Report buildReport(
            UUID id,
            User reporter,
            Category category,
            ReportStatus status,
            String photoPublicId,
            double latitude,
            double longitude
    ) {
        return Report.builder()
                .id(id)
                .reporter(reporter)
                .category(category)
                .description("A pothole")
                .photoUrl("http://cdn.example.com/photo.jpg")
                .photoPublicId(photoPublicId)
                .location(GeoUtils.point(latitude, longitude))
                .status(status)
                .createdAt(LocalDateTime.of(2026, 1, 1, 0, 0))
                .updatedAt(LocalDateTime.of(2026, 1, 1, 0, 0))
                .build();
    }

    private CategoryResponse buildCategoryResponse(Category category) {
        return CategoryResponse.builder()
                .id(category.getId())
                .name(category.getName())
                .slug(category.getSlug())
                .isActive(category.isActive())
                .build();
    }

    private ReportResponse buildReportResponse(Report report) {
        return ReportResponse.builder()
                .id(report.getId())
                .category(buildCategoryResponse(report.getCategory()))
                .description(report.getDescription())
                .photoUrl(report.getPhotoUrl())
                .latitude(report.getLatitude())
                .longitude(report.getLongitude())
                .status(report.getStatus())
                .createdAt(report.getCreatedAt())
                .updatedAt(report.getUpdatedAt())
                .build();
    }

    // ==================================================================
    // createReport
    // ==================================================================
    @Nested
    @DisplayName("createReport")
    class CreateReport {

        @Test
        @DisplayName("saves report + status history and returns mapped response on success")
        void createReportSuccess() {
            var reporter = buildUser(UUID.randomUUID(), UserRole.CITIZEN);
            var category = buildCategory(1L);
            var photo = mock(MultipartFile.class);
            var request = new CreateReportRequest(1L, "Pothole on Jl. Sudirman", photo, JAKARTA_LAT, JAKARTA_LNG);
            var upload = UploadResponse.builder()
                    .id("cloud-id-1")
                    .url("http://cdn.example.com/photo.jpg")
                    .createdAt(LocalDateTime.now())
                    .build();
            var savedReport = buildReport(UUID.randomUUID(), reporter, category, ReportStatus.REPORTED, "cloud-id-1", JAKARTA_LAT, JAKARTA_LNG);
            var expectedResponse = buildReportResponse(savedReport);

            when(categoryRepository.findById(1L)).thenReturn(Optional.of(category));
            when(uploadService.upload(photo)).thenReturn(upload);
            when(reportRepository.save(any(Report.class))).thenReturn(savedReport);
            when(reportMapper.toResponse(savedReport)).thenReturn(expectedResponse);

            var result = reportService.createReport(reporter, request);

            assertThat(result).isEqualTo(expectedResponse);

            var reportCaptor = ArgumentCaptor.forClass(Report.class);
            verify(reportRepository).save(reportCaptor.capture());
            var savedArg = reportCaptor.getValue();
            assertThat(savedArg.getReporter()).isEqualTo(reporter);
            assertThat(savedArg.getCategory()).isEqualTo(category);
            assertThat(savedArg.getDescription()).isEqualTo("Pothole on Jl. Sudirman");
            assertThat(savedArg.getPhotoUrl()).isEqualTo(upload.url());
            assertThat(savedArg.getPhotoPublicId()).isEqualTo(upload.id());
            assertThat(savedArg.getStatus()).isEqualTo(ReportStatus.REPORTED);
            assertThat(savedArg.getLatitude()).isEqualTo(JAKARTA_LAT);
            assertThat(savedArg.getLongitude()).isEqualTo(JAKARTA_LNG);

            var historyCaptor = ArgumentCaptor.forClass(ReportStatusHistory.class);
            verify(reportStatusRepository).save(historyCaptor.capture());
            var historyArg = historyCaptor.getValue();
            assertThat(historyArg.getReport()).isEqualTo(savedReport);
            assertThat(historyArg.getActor()).isEqualTo(reporter);
            assertThat(historyArg.getFromStatus()).isNull();
            assertThat(historyArg.getToStatus()).isEqualTo(ReportStatus.REPORTED);

            verify(uploadService, never()).delete(any());
        }

        @Test
        @DisplayName("throws ResourceNotFoundException when category is not found and never uploads")
        void createReportCategoryNotFound() {
            var reporter = buildUser(UUID.randomUUID(), UserRole.CITIZEN);
            var photo = mock(MultipartFile.class);
            var request = new CreateReportRequest(99L, "desc", photo, JAKARTA_LAT, JAKARTA_LNG);

            when(categoryRepository.findById(99L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> reportService.createReport(reporter, request))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("99");

            verify(uploadService, never()).upload(any());
            verify(reportRepository, never()).save(any());
        }

        @Test
        @DisplayName("throws ValidationException for out-of-range latitude before touching repositories")
        void createReportInvalidLatitude() {
            var reporter = buildUser(UUID.randomUUID(), UserRole.CITIZEN);
            var photo = mock(MultipartFile.class);
            var request = new CreateReportRequest(1L, "desc", photo, 91.0, JAKARTA_LNG);

            assertThatThrownBy(() -> reportService.createReport(reporter, request))
                    .isInstanceOf(ValidationException.class)
                    .hasMessageContaining("Latitude");

            verify(categoryRepository, never()).findById(any());
        }

        @Test
        @DisplayName("throws ValidationException for out-of-range longitude before touching repositories")
        void createReportInvalidLongitude() {
            var reporter = buildUser(UUID.randomUUID(), UserRole.CITIZEN);
            var photo = mock(MultipartFile.class);
            var request = new CreateReportRequest(1L, "desc", photo, JAKARTA_LAT, 181.0);

            assertThatThrownBy(() -> reportService.createReport(reporter, request))
                    .isInstanceOf(ValidationException.class)
                    .hasMessageContaining("Longitude");

            verify(categoryRepository, never()).findById(any());
        }

        @Test
        @DisplayName("deletes the uploaded photo and rethrows when persisting fails")
        void createReportSaveFailureDeletesUpload() {
            var reporter = buildUser(UUID.randomUUID(), UserRole.CITIZEN);
            var category = buildCategory(1L);
            var photo = mock(MultipartFile.class);
            var request = new CreateReportRequest(1L, "desc", photo, JAKARTA_LAT, JAKARTA_LNG);
            var upload = UploadResponse.builder()
                    .id("cloud-id-2")
                    .url("http://cdn.example.com/photo2.jpg")
                    .createdAt(LocalDateTime.now())
                    .build();

            when(categoryRepository.findById(1L)).thenReturn(Optional.of(category));
            when(uploadService.upload(photo)).thenReturn(upload);
            when(reportRepository.save(any(Report.class))).thenThrow(new RuntimeException("db down"));

            assertThatThrownBy(() -> reportService.createReport(reporter, request))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessage("db down");

            verify(uploadService).delete("cloud-id-2");
            verify(reportStatusRepository, never()).save(any());
        }
    }

    // ==================================================================
    // getNearbyReports
    // ==================================================================
    @Nested
    @DisplayName("getNearbyReports")
    class GetNearbyReports {

        @Test
        @DisplayName("returns mapped list from repository query")
        void getNearbyReportsSuccess() {
            var reporter = buildUser(UUID.randomUUID(), UserRole.CITIZEN);
            var category = buildCategory(1L);
            var report1 = buildReport(UUID.randomUUID(), reporter, category, ReportStatus.REPORTED, "p1", JAKARTA_LAT, JAKARTA_LNG);
            var report2 = buildReport(UUID.randomUUID(), reporter, category, ReportStatus.ACKNOWLEDGED, "p2", JAKARTA_LAT + 0.01, JAKARTA_LNG + 0.01);
            var response1 = NearbyReportResponse.builder().id(report1.getId()).build();
            var response2 = NearbyReportResponse.builder().id(report2.getId()).build();

            when(reportRepository.findNearbyReport(JAKARTA_LAT, JAKARTA_LNG, 1000, 20))
                    .thenReturn(List.of(report1, report2));
            when(reportMapper.toNearbyResponse(report1)).thenReturn(response1);
            when(reportMapper.toNearbyResponse(report2)).thenReturn(response2);

            var result = reportService.getNearbyReports(JAKARTA_LAT, JAKARTA_LNG, 1000, 20);

            assertThat(result).containsExactly(response1, response2);
        }

        @Test
        @DisplayName("throws ValidationException for invalid coordinates")
        void getNearbyReportsInvalidCoordinates() {
            assertThatThrownBy(() -> reportService.getNearbyReports(-91.0, JAKARTA_LNG, 1000, 20))
                    .isInstanceOf(ValidationException.class);

            verify(reportRepository, never()).findNearbyReport(anyDouble(), anyDouble(), anyInt(), anyInt());
        }

        @Test
        @DisplayName("throws ValidationException when radius is not positive")
        void getNearbyReportsRadiusNotPositive() {
            assertThatThrownBy(() -> reportService.getNearbyReports(JAKARTA_LAT, JAKARTA_LNG, 0, 20))
                    .isInstanceOf(ValidationException.class)
                    .hasMessageContaining("Radius");
        }

        @Test
        @DisplayName("throws ValidationException when radius exceeds max")
        void getNearbyReportsRadiusExceedsMax() {
            assertThatThrownBy(() -> reportService.getNearbyReports(JAKARTA_LAT, JAKARTA_LNG, 50_001, 20))
                    .isInstanceOf(ValidationException.class)
                    .hasMessageContaining("50000");
        }

        @Test
        @DisplayName("throws ValidationException when limit is not positive")
        void getNearbyReportsLimitNotPositive() {
            assertThatThrownBy(() -> reportService.getNearbyReports(JAKARTA_LAT, JAKARTA_LNG, 1000, 0))
                    .isInstanceOf(ValidationException.class)
                    .hasMessageContaining("Limit");
        }

        @Test
        @DisplayName("throws ValidationException when limit exceeds max")
        void getNearbyReportsLimitExceedsMax() {
            assertThatThrownBy(() -> reportService.getNearbyReports(JAKARTA_LAT, JAKARTA_LNG, 1000, 101))
                    .isInstanceOf(ValidationException.class)
                    .hasMessageContaining("100");
        }
    }

    // ==================================================================
    // getReportDetail
    // ==================================================================
    @Nested
    @DisplayName("getReportDetail")
    class GetReportDetail {

        @Test
        @DisplayName("returns mapped response when found")
        void getReportDetailSuccess() {
            var reporter = buildUser(UUID.randomUUID(), UserRole.CITIZEN);
            var category = buildCategory(1L);
            var report = buildReport(UUID.randomUUID(), reporter, category, ReportStatus.REPORTED, "p1", JAKARTA_LAT, JAKARTA_LNG);
            var expected = buildReportResponse(report);

            when(reportRepository.findById(report.getId())).thenReturn(Optional.of(report));
            when(reportMapper.toResponse(report)).thenReturn(expected);

            var result = reportService.getReportDetail(report.getId());

            assertThat(result).isEqualTo(expected);
        }

        @Test
        @DisplayName("throws ResourceNotFoundException when not found")
        void getReportDetailNotFound() {
            var id = UUID.randomUUID();
            when(reportRepository.findById(id)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> reportService.getReportDetail(id))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining(id.toString());
        }
    }

    // ==================================================================
    // getReportHistory
    // ==================================================================
    @Nested
    @DisplayName("getReportHistory")
    class GetReportHistory {

        @Test
        @DisplayName("returns mapped history when report exists")
        void getReportHistorySuccess() {
            var id = UUID.randomUUID();
            var reporter = buildUser(UUID.randomUUID(), UserRole.CITIZEN);
            var category = buildCategory(1L);
            var report = buildReport(id, reporter, category, ReportStatus.ACKNOWLEDGED, "p1", JAKARTA_LAT, JAKARTA_LNG);
            var history = ReportStatusHistory.builder()
                    .report(report)
                    .actor(reporter)
                    .fromStatus(ReportStatus.REPORTED)
                    .toStatus(ReportStatus.ACKNOWLEDGED)
                    .createdAt(LocalDateTime.now())
                    .build();
            var expected = List.of(ReportStatusResponse.builder()
                    .fromStatus(ReportStatus.REPORTED)
                    .toStatus(ReportStatus.ACKNOWLEDGED)
                    .build());

            when(reportRepository.existsById(id)).thenReturn(true);
            when(reportStatusRepository.findByReportIdOrderByCreatedAtAsc(id)).thenReturn(List.of(history));
            when(reportStatusMapper.toResponse(List.of(history))).thenReturn(expected);

            var result = reportService.getReportHistory(id);

            assertThat(result).isEqualTo(expected);
        }

        @Test
        @DisplayName("throws ResourceNotFoundException when report does not exist")
        void getReportHistoryNotFound() {
            var id = UUID.randomUUID();
            when(reportRepository.existsById(id)).thenReturn(false);

            assertThatThrownBy(() -> reportService.getReportHistory(id))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining(id.toString());

            verify(reportStatusRepository, never()).findByReportIdOrderByCreatedAtAsc(any());
        }
    }

    // ==================================================================
    // getMyReports
    // ==================================================================
    @Nested
    @DisplayName("getMyReports")
    class GetMyReports {

        @Test
        @DisplayName("builds an offset pageable, maps content, and returns page metadata")
        void getMyReportsSuccess() {
            var reporter = buildUser(UUID.randomUUID(), UserRole.CITIZEN);
            var category = buildCategory(1L);
            var report1 = buildReport(UUID.randomUUID(), reporter, category, ReportStatus.REPORTED, "p1", JAKARTA_LAT, JAKARTA_LNG);
            var report2 = buildReport(UUID.randomUUID(), reporter, category, ReportStatus.RESOLVED, "p2", JAKARTA_LAT, JAKARTA_LNG);
            var response1 = buildReportResponse(report1);
            var response2 = buildReportResponse(report2);

            when(reportRepository.findByReporterId(eq(reporter.getId()), any(Pageable.class)))
                    .thenAnswer(invocation -> {
                        Pageable pageable = invocation.getArgument(1);
                        return new PageImpl<Report>(List.of(report1, report2), pageable, 5);
                    });
            when(reportMapper.toResponse(report1)).thenReturn(response1);
            when(reportMapper.toResponse(report2)).thenReturn(response2);

            var result = reportService.getMyReports(reporter, 2, 0);

            assertThat(result.items()).containsExactly(response1, response2);
            assertThat(result.meta().limit()).isEqualTo(2);
            assertThat(result.meta().offset()).isEqualTo(0);
            assertThat(result.meta().total()).isEqualTo(5);
            assertThat(result.meta().hasNext()).isTrue();

            var pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
            verify(reportRepository).findByReporterId(eq(reporter.getId()), pageableCaptor.capture());
            var pageable = pageableCaptor.getValue();
            assertThat(pageable).isInstanceOf(OffsetPageable.class);
            assertThat(pageable.getOffset()).isEqualTo(0);
            assertThat(pageable.getPageSize()).isEqualTo(2);
            assertThat(pageable.getSort()).isEqualTo(Sort.by(Sort.Direction.DESC, "createdAt"));
        }

        @Test
        @DisplayName("throws ValidationException when limit is not positive")
        void getMyReportsLimitNotPositive() {
            var reporter = buildUser(UUID.randomUUID(), UserRole.CITIZEN);

            assertThatThrownBy(() -> reportService.getMyReports(reporter, 0, 0))
                    .isInstanceOf(ValidationException.class)
                    .hasMessageContaining("Limit");
        }

        @Test
        @DisplayName("throws ValidationException when limit exceeds max page size")
        void getMyReportsLimitExceedsMax() {
            var reporter = buildUser(UUID.randomUUID(), UserRole.CITIZEN);

            assertThatThrownBy(() -> reportService.getMyReports(reporter, 101, 0))
                    .isInstanceOf(ValidationException.class)
                    .hasMessageContaining("100");
        }

        @Test
        @DisplayName("throws ValidationException when offset is negative")
        void getMyReportsOffsetNegative() {
            var reporter = buildUser(UUID.randomUUID(), UserRole.CITIZEN);

            assertThatThrownBy(() -> reportService.getMyReports(reporter, 10, -1))
                    .isInstanceOf(ValidationException.class)
                    .hasMessageContaining("Offset");
        }
    }

    // ==================================================================
    // updateReportStatus
    // ==================================================================
    @Nested
    @DisplayName("updateReportStatus")
    class UpdateReportStatus {

        @Test
        @DisplayName("applies an allowed transition without a note and records history")
        void updateReportStatusSuccessNoNoteRequired() {
            var reporter = buildUser(UUID.randomUUID(), UserRole.CITIZEN);
            var resolver = buildUser(UUID.randomUUID(), UserRole.RESOLVER);
            var category = buildCategory(1L);
            var report = buildReport(UUID.randomUUID(), reporter, category, ReportStatus.REPORTED, "p1", JAKARTA_LAT, JAKARTA_LNG);
            var request = new UpdateStatusRequest(ReportStatus.ACKNOWLEDGED, null);
            var expected = buildReportResponse(report);

            when(reportRepository.findById(report.getId())).thenReturn(Optional.of(report));
            when(reportRepository.save(report)).thenReturn(report);
            when(reportMapper.toResponse(report)).thenReturn(expected);

            var result = reportService.updateReportStatus(report.getId(), resolver, request);

            assertThat(result).isEqualTo(expected);
            assertThat(report.getStatus()).isEqualTo(ReportStatus.ACKNOWLEDGED);

            var historyCaptor = ArgumentCaptor.forClass(ReportStatusHistory.class);
            verify(reportStatusRepository).save(historyCaptor.capture());
            var history = historyCaptor.getValue();
            assertThat(history.getReport()).isEqualTo(report);
            assertThat(history.getActor()).isEqualTo(resolver);
            assertThat(history.getFromStatus()).isEqualTo(ReportStatus.REPORTED);
            assertThat(history.getToStatus()).isEqualTo(ReportStatus.ACKNOWLEDGED);
            assertThat(history.getNote()).isNull();
        }

        @Test
        @DisplayName("rejecting with a note transitions successfully and stores the note")
        void updateReportStatusRejectWithNote() {
            var reporter = buildUser(UUID.randomUUID(), UserRole.CITIZEN);
            var resolver = buildUser(UUID.randomUUID(), UserRole.RESOLVER);
            var category = buildCategory(1L);
            var report = buildReport(UUID.randomUUID(), reporter, category, ReportStatus.REPORTED, "p1", JAKARTA_LAT, JAKARTA_LNG);
            var request = new UpdateStatusRequest(ReportStatus.REJECTED, "Duplicate report");
            var expected = buildReportResponse(report);

            when(reportRepository.findById(report.getId())).thenReturn(Optional.of(report));
            when(reportRepository.save(report)).thenReturn(report);
            when(reportMapper.toResponse(report)).thenReturn(expected);

            var result = reportService.updateReportStatus(report.getId(), resolver, request);

            assertThat(result).isEqualTo(expected);
            assertThat(report.getStatus()).isEqualTo(ReportStatus.REJECTED);

            var historyCaptor = ArgumentCaptor.forClass(ReportStatusHistory.class);
            verify(reportStatusRepository).save(historyCaptor.capture());
            assertThat(historyCaptor.getValue().getNote()).isEqualTo("Duplicate report");
        }

        @Test
        @DisplayName("throws ValidationException when rejecting without a note")
        void updateReportStatusRejectMissingNote() {
            var reporter = buildUser(UUID.randomUUID(), UserRole.CITIZEN);
            var resolver = buildUser(UUID.randomUUID(), UserRole.RESOLVER);
            var category = buildCategory(1L);
            var report = buildReport(UUID.randomUUID(), reporter, category, ReportStatus.REPORTED, "p1", JAKARTA_LAT, JAKARTA_LNG);
            var request = new UpdateStatusRequest(ReportStatus.REJECTED, "   ");

            when(reportRepository.findById(report.getId())).thenReturn(Optional.of(report));

            assertThatThrownBy(() -> reportService.updateReportStatus(report.getId(), resolver, request))
                    .isInstanceOf(ValidationException.class)
                    .hasMessageContaining("note");

            verify(reportRepository, never()).save(any());
            verify(reportStatusRepository, never()).save(any());
        }

        @Test
        @DisplayName("throws IllegalTransitionException for a disallowed transition")
        void updateReportStatusIllegalTransition() {
            var reporter = buildUser(UUID.randomUUID(), UserRole.CITIZEN);
            var resolver = buildUser(UUID.randomUUID(), UserRole.RESOLVER);
            var category = buildCategory(1L);
            var report = buildReport(UUID.randomUUID(), reporter, category, ReportStatus.REPORTED, "p1", JAKARTA_LAT, JAKARTA_LNG);
            var request = new UpdateStatusRequest(ReportStatus.RESOLVED, null);

            when(reportRepository.findById(report.getId())).thenReturn(Optional.of(report));

            assertThatThrownBy(() -> reportService.updateReportStatus(report.getId(), resolver, request))
                    .isInstanceOf(IllegalTransitionException.class);

            assertThat(report.getStatus()).isEqualTo(ReportStatus.REPORTED);
            verify(reportRepository, never()).save(any());
            verify(reportStatusRepository, never()).save(any());
        }

        @Test
        @DisplayName("throws ResourceNotFoundException when report does not exist")
        void updateReportStatusReportNotFound() {
            var id = UUID.randomUUID();
            var resolver = buildUser(UUID.randomUUID(), UserRole.RESOLVER);
            var request = new UpdateStatusRequest(ReportStatus.ACKNOWLEDGED, null);

            when(reportRepository.findById(id)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> reportService.updateReportStatus(id, resolver, request))
                    .isInstanceOf(ResourceNotFoundException.class);

            verify(reportStatusRepository, never()).save(any());
        }
    }

    // ==================================================================
    // updateReport
    // ==================================================================
    @Nested
    @DisplayName("updateReport")
    class UpdateReport {

        @Test
        @DisplayName("updates fields without touching photo when no new photo is provided")
        void updateReportSuccessNoPhoto() {
            var reporter = buildUser(UUID.randomUUID(), UserRole.CITIZEN);
            var oldCategory = buildCategory(1L);
            var newCategory = buildCategory(2L);
            var report = buildReport(UUID.randomUUID(), reporter, oldCategory, ReportStatus.REPORTED, "old-photo", JAKARTA_LAT, JAKARTA_LNG);
            var request = new UpdateReportRequest(2L, "Updated description", null, JAKARTA_LAT + 0.1, JAKARTA_LNG + 0.1);
            var expected = buildReportResponse(report);

            when(reportRepository.findById(report.getId())).thenReturn(Optional.of(report));
            when(categoryRepository.findById(2L)).thenReturn(Optional.of(newCategory));
            when(reportRepository.save(report)).thenReturn(report);
            when(reportMapper.toResponse(report)).thenReturn(expected);

            var result = reportService.updateReport(report.getId(), reporter, request);

            assertThat(result).isEqualTo(expected);
            assertThat(report.getCategory()).isEqualTo(newCategory);
            assertThat(report.getDescription()).isEqualTo("Updated description");
            assertThat(report.getLatitude()).isEqualTo(JAKARTA_LAT + 0.1);
            assertThat(report.getLongitude()).isEqualTo(JAKARTA_LNG + 0.1);
            assertThat(report.getPhotoPublicId()).isEqualTo("old-photo");

            verify(uploadService, never()).upload(any());
            verify(uploadService, never()).delete(any());
        }

        @Test
        @DisplayName("uploads new photo, saves, and deletes the old photo asset on success")
        void updateReportSuccessWithPhotoDeletesOldUpload() {
            var reporter = buildUser(UUID.randomUUID(), UserRole.CITIZEN);
            var category = buildCategory(1L);
            var report = buildReport(UUID.randomUUID(), reporter, category, ReportStatus.REPORTED, "old-photo", JAKARTA_LAT, JAKARTA_LNG);
            var photo = mock(MultipartFile.class);
            when(photo.isEmpty()).thenReturn(false);
            var request = new UpdateReportRequest(1L, "Updated description", photo, JAKARTA_LAT, JAKARTA_LNG);
            var upload = UploadResponse.builder().id("new-photo").url("http://cdn.example.com/new.jpg").createdAt(LocalDateTime.now()).build();
            var expected = buildReportResponse(report);

            when(reportRepository.findById(report.getId())).thenReturn(Optional.of(report));
            when(categoryRepository.findById(1L)).thenReturn(Optional.of(category));
            when(uploadService.upload(photo)).thenReturn(upload);
            when(reportRepository.save(report)).thenReturn(report);
            when(reportMapper.toResponse(report)).thenReturn(expected);

            var result = reportService.updateReport(report.getId(), reporter, request);

            assertThat(result).isEqualTo(expected);
            assertThat(report.getPhotoUrl()).isEqualTo(upload.url());
            assertThat(report.getPhotoPublicId()).isEqualTo(upload.id());

            verify(uploadService).delete("old-photo");
            verify(uploadService, never()).delete("new-photo");
        }

        @Test
        @DisplayName("does not delete any upload when report previously had no photo public id")
        void updateReportSuccessWithPhotoNoOldUpload() {
            var reporter = buildUser(UUID.randomUUID(), UserRole.CITIZEN);
            var category = buildCategory(1L);
            var report = buildReport(UUID.randomUUID(), reporter, category, ReportStatus.REPORTED, null, JAKARTA_LAT, JAKARTA_LNG);
            var photo = mock(MultipartFile.class);
            when(photo.isEmpty()).thenReturn(false);
            var request = new UpdateReportRequest(1L, "Updated description", photo, JAKARTA_LAT, JAKARTA_LNG);
            var upload = UploadResponse.builder().id("new-photo").url("http://cdn.example.com/new.jpg").createdAt(LocalDateTime.now()).build();
            var expected = buildReportResponse(report);

            when(reportRepository.findById(report.getId())).thenReturn(Optional.of(report));
            when(categoryRepository.findById(1L)).thenReturn(Optional.of(category));
            when(uploadService.upload(photo)).thenReturn(upload);
            when(reportRepository.save(report)).thenReturn(report);
            when(reportMapper.toResponse(report)).thenReturn(expected);

            var result = reportService.updateReport(report.getId(), reporter, request);

            assertThat(result).isEqualTo(expected);
            verify(uploadService, never()).delete(any());
        }

        @Test
        @DisplayName("deletes the newly uploaded photo and rethrows when save fails")
        void updateReportSaveFailureDeletesNewUpload() {
            var reporter = buildUser(UUID.randomUUID(), UserRole.CITIZEN);
            var category = buildCategory(1L);
            var report = buildReport(UUID.randomUUID(), reporter, category, ReportStatus.REPORTED, "old-photo", JAKARTA_LAT, JAKARTA_LNG);
            var photo = mock(MultipartFile.class);
            when(photo.isEmpty()).thenReturn(false);
            var request = new UpdateReportRequest(1L, "Updated description", photo, JAKARTA_LAT, JAKARTA_LNG);
            var upload = UploadResponse.builder().id("new-photo").url("http://cdn.example.com/new.jpg").createdAt(LocalDateTime.now()).build();

            when(reportRepository.findById(report.getId())).thenReturn(Optional.of(report));
            when(categoryRepository.findById(1L)).thenReturn(Optional.of(category));
            when(uploadService.upload(photo)).thenReturn(upload);
            when(reportRepository.save(report)).thenThrow(new RuntimeException("db down"));

            assertThatThrownBy(() -> reportService.updateReport(report.getId(), reporter, request))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessage("db down");

            verify(uploadService).delete("new-photo");
            verify(uploadService, never()).delete("old-photo");
        }

        @Test
        @DisplayName("throws ResourceNotFoundException when report does not exist")
        void updateReportNotFound() {
            var id = UUID.randomUUID();
            var requester = buildUser(UUID.randomUUID(), UserRole.CITIZEN);
            var request = new UpdateReportRequest(1L, "desc", null, JAKARTA_LAT, JAKARTA_LNG);

            when(reportRepository.findById(id)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> reportService.updateReport(id, requester, request))
                    .isInstanceOf(ResourceNotFoundException.class);
        }

        @Test
        @DisplayName("throws AccessDeniedException when requester is not the report owner")
        void updateReportNotOwner() {
            var reporter = buildUser(UUID.randomUUID(), UserRole.CITIZEN);
            var otherUser = buildUser(UUID.randomUUID(), UserRole.CITIZEN);
            var category = buildCategory(1L);
            var report = buildReport(UUID.randomUUID(), reporter, category, ReportStatus.REPORTED, "p1", JAKARTA_LAT, JAKARTA_LNG);
            var request = new UpdateReportRequest(1L, "desc", null, JAKARTA_LAT, JAKARTA_LNG);

            when(reportRepository.findById(report.getId())).thenReturn(Optional.of(report));

            assertThatThrownBy(() -> reportService.updateReport(report.getId(), otherUser, request))
                    .isInstanceOf(AccessDeniedException.class);

            verify(categoryRepository, never()).findById(any());
        }

        @Test
        @DisplayName("throws IllegalTransitionException when report is no longer editable")
        void updateReportNotEditable() {
            var reporter = buildUser(UUID.randomUUID(), UserRole.CITIZEN);
            var category = buildCategory(1L);
            var report = buildReport(UUID.randomUUID(), reporter, category, ReportStatus.ACKNOWLEDGED, "p1", JAKARTA_LAT, JAKARTA_LNG);
            var request = new UpdateReportRequest(1L, "desc", null, JAKARTA_LAT, JAKARTA_LNG);

            when(reportRepository.findById(report.getId())).thenReturn(Optional.of(report));

            assertThatThrownBy(() -> reportService.updateReport(report.getId(), reporter, request))
                    .isInstanceOf(IllegalTransitionException.class);

            verify(categoryRepository, never()).findById(any());
        }

        @Test
        @DisplayName("throws ValidationException for invalid coordinates")
        void updateReportInvalidCoordinates() {
            var reporter = buildUser(UUID.randomUUID(), UserRole.CITIZEN);
            var category = buildCategory(1L);
            var report = buildReport(UUID.randomUUID(), reporter, category, ReportStatus.REPORTED, "p1", JAKARTA_LAT, JAKARTA_LNG);
            var request = new UpdateReportRequest(1L, "desc", null, 200.0, JAKARTA_LNG);

            when(reportRepository.findById(report.getId())).thenReturn(Optional.of(report));

            assertThatThrownBy(() -> reportService.updateReport(report.getId(), reporter, request))
                    .isInstanceOf(ValidationException.class);

            verify(categoryRepository, never()).findById(any());
        }

        @Test
        @DisplayName("throws ResourceNotFoundException when category does not exist")
        void updateReportCategoryNotFound() {
            var reporter = buildUser(UUID.randomUUID(), UserRole.CITIZEN);
            var category = buildCategory(1L);
            var report = buildReport(UUID.randomUUID(), reporter, category, ReportStatus.REPORTED, "p1", JAKARTA_LAT, JAKARTA_LNG);
            var request = new UpdateReportRequest(99L, "desc", null, JAKARTA_LAT, JAKARTA_LNG);

            when(reportRepository.findById(report.getId())).thenReturn(Optional.of(report));
            when(categoryRepository.findById(99L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> reportService.updateReport(report.getId(), reporter, request))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("99");

            verify(reportRepository, never()).save(any());
        }
    }

    // ==================================================================
    // deleteReport
    // ==================================================================
    @Nested
    @DisplayName("deleteReport")
    class DeleteReport {

        @Test
        @DisplayName("deletes report and does not touch upload service when there is no photo public id")
        void deleteReportSuccessNoPhoto() {
            var reporter = buildUser(UUID.randomUUID(), UserRole.CITIZEN);
            var category = buildCategory(1L);
            var report = buildReport(UUID.randomUUID(), reporter, category, ReportStatus.REPORTED, null, JAKARTA_LAT, JAKARTA_LNG);

            when(reportRepository.findById(report.getId())).thenReturn(Optional.of(report));

            reportService.deleteReport(report.getId(), reporter);

            verify(reportRepository).delete(report);
            verify(uploadService, never()).delete(any());
        }

        @Test
        @DisplayName("deletes report and the associated upload asset when photo public id is present")
        void deleteReportSuccessWithPhoto() {
            var reporter = buildUser(UUID.randomUUID(), UserRole.CITIZEN);
            var category = buildCategory(1L);
            var report = buildReport(UUID.randomUUID(), reporter, category, ReportStatus.REPORTED, "photo-1", JAKARTA_LAT, JAKARTA_LNG);

            when(reportRepository.findById(report.getId())).thenReturn(Optional.of(report));

            reportService.deleteReport(report.getId(), reporter);

            verify(reportRepository).delete(report);
            verify(uploadService).delete("photo-1");
        }

        @Test
        @DisplayName("swallows exceptions raised while deleting the upload asset")
        void deleteReportUploadDeleteFailureIsSwallowed() {
            var reporter = buildUser(UUID.randomUUID(), UserRole.CITIZEN);
            var category = buildCategory(1L);
            var report = buildReport(UUID.randomUUID(), reporter, category, ReportStatus.REPORTED, "photo-1", JAKARTA_LAT, JAKARTA_LNG);

            when(reportRepository.findById(report.getId())).thenReturn(Optional.of(report));
            org.mockito.Mockito.doThrow(new RuntimeException("cloud error")).when(uploadService).delete("photo-1");

            reportService.deleteReport(report.getId(), reporter);

            verify(reportRepository).delete(report);
            verify(uploadService).delete("photo-1");
        }

        @Test
        @DisplayName("throws ResourceNotFoundException when report does not exist")
        void deleteReportNotFound() {
            var id = UUID.randomUUID();
            var requester = buildUser(UUID.randomUUID(), UserRole.CITIZEN);

            when(reportRepository.findById(id)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> reportService.deleteReport(id, requester))
                    .isInstanceOf(ResourceNotFoundException.class);

            verify(reportRepository, never()).delete(any());
        }

        @Test
        @DisplayName("throws AccessDeniedException when requester is not the report owner")
        void deleteReportNotOwner() {
            var reporter = buildUser(UUID.randomUUID(), UserRole.CITIZEN);
            var otherUser = buildUser(UUID.randomUUID(), UserRole.CITIZEN);
            var category = buildCategory(1L);
            var report = buildReport(UUID.randomUUID(), reporter, category, ReportStatus.REPORTED, "p1", JAKARTA_LAT, JAKARTA_LNG);

            when(reportRepository.findById(report.getId())).thenReturn(Optional.of(report));

            assertThatThrownBy(() -> reportService.deleteReport(report.getId(), otherUser))
                    .isInstanceOf(AccessDeniedException.class);

            verify(reportRepository, never()).delete(any());
        }

        @Test
        @DisplayName("throws IllegalTransitionException when report is no longer editable")
        void deleteReportNotEditable() {
            var reporter = buildUser(UUID.randomUUID(), UserRole.CITIZEN);
            var category = buildCategory(1L);
            var report = buildReport(UUID.randomUUID(), reporter, category, ReportStatus.RESOLVED, "p1", JAKARTA_LAT, JAKARTA_LNG);

            when(reportRepository.findById(report.getId())).thenReturn(Optional.of(report));

            assertThatThrownBy(() -> reportService.deleteReport(report.getId(), reporter))
                    .isInstanceOf(IllegalTransitionException.class);

            verify(reportRepository, never()).delete(any());
        }
    }

    // ==================================================================
    // getQueue
    // ==================================================================
    @Nested
    @DisplayName("getQueue")
    class GetQueue {

        @Test
        @DisplayName("returns queue items with computed distance and aggregated counts")
        void getQueueSuccess() {
            var reporter = buildUser(UUID.randomUUID(), UserRole.CITIZEN);
            var category = buildCategory(1L);
            double nearbyLat = JAKARTA_LAT + 0.001;
            double nearbyLng = JAKARTA_LNG + 0.001;
            var report1 = buildReport(UUID.randomUUID(), reporter, category, ReportStatus.REPORTED, "p1", nearbyLat, nearbyLng);
            var report2 = buildReport(UUID.randomUUID(), reporter, category, ReportStatus.ACKNOWLEDGED, "p2", JAKARTA_LAT, JAKARTA_LNG);

            var queueResponse1 = QueueReportResponse.builder()
                    .id(report1.getId())
                    .category(buildCategoryResponse(category))
                    .description(report1.getDescription())
                    .photoUrl(report1.getPhotoUrl())
                    .status(report1.getStatus())
                    .latitude(report1.getLatitude())
                    .longitude(report1.getLongitude())
                    .createdAt(report1.getCreatedAt())
                    .build();
            var queueResponse2 = QueueReportResponse.builder()
                    .id(report2.getId())
                    .category(buildCategoryResponse(category))
                    .description(report2.getDescription())
                    .photoUrl(report2.getPhotoUrl())
                    .status(report2.getStatus())
                    .latitude(report2.getLatitude())
                    .longitude(report2.getLongitude())
                    .createdAt(report2.getCreatedAt())
                    .build();

            when(reportRepository.findQueueReports(
                    eq(List.of("REPORTED", "ACKNOWLEDGED")),
                    eq(JAKARTA_LAT),
                    eq(JAKARTA_LNG),
                    eq(2000),
                    any(Pageable.class)
            )).thenAnswer(invocation -> {
                Pageable pageable = invocation.getArgument(4);
                return new PageImpl<Report>(List.of(report1, report2), pageable, 2);
            });
            when(reportMapper.toQueueResponse(report1)).thenReturn(queueResponse1);
            when(reportMapper.toQueueResponse(report2)).thenReturn(queueResponse2);

            var reportedCount = mock(StatusCountProjection.class);
            when(reportedCount.getStatus()).thenReturn("REPORTED");
            when(reportedCount.getCount()).thenReturn(3L);
            var acknowledgedCount = mock(StatusCountProjection.class);
            when(acknowledgedCount.getStatus()).thenReturn("ACKNOWLEDGED");
            when(acknowledgedCount.getCount()).thenReturn(2L);
            var inProgressCount = mock(StatusCountProjection.class);
            when(inProgressCount.getStatus()).thenReturn("IN_PROGRESS");
            when(inProgressCount.getCount()).thenReturn(1L);
            var resolvedCount = mock(StatusCountProjection.class);
            when(resolvedCount.getStatus()).thenReturn("RESOLVED");
            when(resolvedCount.getCount()).thenReturn(4L);

            when(reportRepository.countQueueReportsByStatus(JAKARTA_LAT, JAKARTA_LNG, 2000))
                    .thenReturn(List.of(reportedCount, acknowledgedCount, inProgressCount, resolvedCount));

            var result = reportService.getQueue(QueueTab.OPEN, JAKARTA_LAT, JAKARTA_LNG, 2000, 10, 0);

            assertThat(result.items()).hasSize(2);
            var expectedDistance1 = GeoUtils.distanceMeters(JAKARTA_LAT, JAKARTA_LNG, nearbyLat, nearbyLng);
            var expectedDistance2 = GeoUtils.distanceMeters(JAKARTA_LAT, JAKARTA_LNG, JAKARTA_LAT, JAKARTA_LNG);
            assertThat(result.items().get(0).distanceMeter()).isEqualTo(expectedDistance1);
            assertThat(result.items().get(0).id()).isEqualTo(report1.getId());
            assertThat(result.items().get(1).distanceMeter()).isEqualTo(expectedDistance2);
            assertThat(result.items().get(1).id()).isEqualTo(report2.getId());

            assertThat(result.meta().limit()).isEqualTo(10);
            assertThat(result.meta().offset()).isEqualTo(0);
            assertThat(result.meta().total()).isEqualTo(2);
            assertThat(result.meta().hasNext()).isFalse();

            assertThat(result.counts()).isEqualTo(new QueueCounts(5, 1, 4));

            var pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
            verify(reportRepository).findQueueReports(any(), anyDouble(), anyDouble(), anyInt(), pageableCaptor.capture());
            var pageable = pageableCaptor.getValue();
            assertThat(pageable.getOffset()).isEqualTo(0);
            assertThat(pageable.getPageSize()).isEqualTo(10);
            assertThat(pageable.getSort()).isEqualTo(Sort.unsorted());
        }

        @Test
        @DisplayName("throws ValidationException for invalid coordinates")
        void getQueueInvalidCoordinates() {
            assertThatThrownBy(() -> reportService.getQueue(QueueTab.OPEN, -100.0, JAKARTA_LNG, 2000, 10, 0))
                    .isInstanceOf(ValidationException.class);

            verify(reportRepository, never()).findQueueReports(any(), anyDouble(), anyDouble(), anyInt(), any());
        }

        @Test
        @DisplayName("throws ValidationException when radius is not positive")
        void getQueueRadiusNotPositive() {
            assertThatThrownBy(() -> reportService.getQueue(QueueTab.OPEN, JAKARTA_LAT, JAKARTA_LNG, 0, 10, 0))
                    .isInstanceOf(ValidationException.class)
                    .hasMessageContaining("Radius");
        }

        @Test
        @DisplayName("throws ValidationException when radius exceeds max")
        void getQueueRadiusExceedsMax() {
            assertThatThrownBy(() -> reportService.getQueue(QueueTab.OPEN, JAKARTA_LAT, JAKARTA_LNG, 50_001, 10, 0))
                    .isInstanceOf(ValidationException.class)
                    .hasMessageContaining("50000");
        }

        @Test
        @DisplayName("throws ValidationException when limit is not positive")
        void getQueueLimitNotPositive() {
            assertThatThrownBy(() -> reportService.getQueue(QueueTab.OPEN, JAKARTA_LAT, JAKARTA_LNG, 2000, 0, 0))
                    .isInstanceOf(ValidationException.class)
                    .hasMessageContaining("Limit");
        }

        @Test
        @DisplayName("throws ValidationException when limit exceeds max")
        void getQueueLimitExceedsMax() {
            assertThatThrownBy(() -> reportService.getQueue(QueueTab.OPEN, JAKARTA_LAT, JAKARTA_LNG, 2000, 101, 0))
                    .isInstanceOf(ValidationException.class)
                    .hasMessageContaining("100");
        }

        @Test
        @DisplayName("throws ValidationException when offset is negative")
        void getQueueOffsetNegative() {
            assertThatThrownBy(() -> reportService.getQueue(QueueTab.OPEN, JAKARTA_LAT, JAKARTA_LNG, 2000, 10, -1))
                    .isInstanceOf(ValidationException.class)
                    .hasMessageContaining("Offset");
        }
    }
}
