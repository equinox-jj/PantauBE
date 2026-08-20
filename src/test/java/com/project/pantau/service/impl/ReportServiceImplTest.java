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
import com.project.pantau.entity.*;
import com.project.pantau.enums.QueueTab;
import com.project.pantau.enums.ReportStatus;
import com.project.pantau.enums.UserRole;
import com.project.pantau.mapper.ReportMapper;
import com.project.pantau.mapper.ReportStatusMapper;
import com.project.pantau.repository.CategoryRepository;
import com.project.pantau.repository.ReportPhotoRepository;
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
    private ReportPhotoRepository reportPhotoRepository;
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
                reportPhotoRepository,
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
            double latitude,
            double longitude
    ) {
        return Report.builder()
                .id(id)
                .reporter(reporter)
                .category(category)
                .description("A pothole")
                .location(GeoUtils.point(latitude, longitude))
                .status(status)
                .createdAt(LocalDateTime.of(2026, 1, 1, 0, 0))
                .updatedAt(LocalDateTime.of(2026, 1, 1, 0, 0))
                .build();
    }

    private ReportPhoto buildReportPhoto(Report report, String url, String publicId, int position) {
        return ReportPhoto.builder()
                .id(UUID.randomUUID())
                .report(report)
                .photoUrl(url)
                .photoPublicId(publicId)
                .position(position)
                .createdAt(LocalDateTime.of(2026, 1, 1, 0, 0))
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

    private ReportResponse buildReportResponse(Report report, List<String> photoUrls) {
        return ReportResponse.builder()
                .id(report.getId())
                .category(buildCategoryResponse(report.getCategory()))
                .description(report.getDescription())
                .photoUrls(photoUrls)
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
        @DisplayName("uploads every photo, saves report + photos + status history, returns mapped response")
        void createReportSuccess() {
            var reporter = buildUser(UUID.randomUUID(), UserRole.CITIZEN);
            var category = buildCategory(1L);
            var photo1 = mock(MultipartFile.class);
            var photo2 = mock(MultipartFile.class);
            var request = new CreateReportRequest(1L, "Pothole on Jl. Sudirman", List.of(photo1, photo2), JAKARTA_LAT, JAKARTA_LNG);
            var upload1 = UploadResponse.builder().id("cloud-id-1").url("http://cdn.example.com/photo1.jpg").createdAt(LocalDateTime.now()).build();
            var upload2 = UploadResponse.builder().id("cloud-id-2").url("http://cdn.example.com/photo2.jpg").createdAt(LocalDateTime.now()).build();
            var savedReport = buildReport(UUID.randomUUID(), reporter, category, ReportStatus.REPORTED, JAKARTA_LAT, JAKARTA_LNG);
            var expectedResponse = buildReportResponse(savedReport, List.of(upload1.url(), upload2.url()));

            when(categoryRepository.findById(1L)).thenReturn(Optional.of(category));
            when(uploadService.upload(photo1)).thenReturn(upload1);
            when(uploadService.upload(photo2)).thenReturn(upload2);
            when(reportRepository.save(any(Report.class))).thenReturn(savedReport);
            when(reportPhotoRepository.saveAll(anyList())).thenAnswer(inv -> inv.getArgument(0));
            when(reportMapper.toResponse(savedReport, List.of(upload1.url(), upload2.url()))).thenReturn(expectedResponse);

            var result = reportService.createReport(reporter, request);

            assertThat(result).isEqualTo(expectedResponse);

            var reportCaptor = ArgumentCaptor.forClass(Report.class);
            verify(reportRepository).save(reportCaptor.capture());
            var savedArg = reportCaptor.getValue();
            assertThat(savedArg.getReporter()).isEqualTo(reporter);
            assertThat(savedArg.getCategory()).isEqualTo(category);
            assertThat(savedArg.getDescription()).isEqualTo("Pothole on Jl. Sudirman");
            assertThat(savedArg.getStatus()).isEqualTo(ReportStatus.REPORTED);
            assertThat(savedArg.getLatitude()).isEqualTo(JAKARTA_LAT);
            assertThat(savedArg.getLongitude()).isEqualTo(JAKARTA_LNG);

            @SuppressWarnings("unchecked")
            var photosCaptor = ArgumentCaptor.forClass(List.class);
            verify(reportPhotoRepository).saveAll(photosCaptor.capture());
            List<ReportPhoto> savedPhotos = photosCaptor.getValue();
            assertThat(savedPhotos).hasSize(2);
            assertThat(savedPhotos.get(0).getReport()).isEqualTo(savedReport);
            assertThat(savedPhotos.get(0).getPhotoUrl()).isEqualTo(upload1.url());
            assertThat(savedPhotos.get(0).getPhotoPublicId()).isEqualTo(upload1.id());
            assertThat(savedPhotos.get(0).getPosition()).isEqualTo(0);
            assertThat(savedPhotos.get(1).getPhotoUrl()).isEqualTo(upload2.url());
            assertThat(savedPhotos.get(1).getPosition()).isEqualTo(1);

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
            var request = new CreateReportRequest(99L, "desc", List.of(photo), JAKARTA_LAT, JAKARTA_LNG);

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
            var request = new CreateReportRequest(1L, "desc", List.of(photo), 91.0, JAKARTA_LNG);

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
            var request = new CreateReportRequest(1L, "desc", List.of(photo), JAKARTA_LAT, 181.0);

            assertThatThrownBy(() -> reportService.createReport(reporter, request))
                    .isInstanceOf(ValidationException.class)
                    .hasMessageContaining("Longitude");

            verify(categoryRepository, never()).findById(any());
        }

        @Test
        @DisplayName("deletes all uploaded photos and rethrows when persisting fails")
        void createReportSaveFailureDeletesAllUploads() {
            var reporter = buildUser(UUID.randomUUID(), UserRole.CITIZEN);
            var category = buildCategory(1L);
            var photo1 = mock(MultipartFile.class);
            var photo2 = mock(MultipartFile.class);
            var request = new CreateReportRequest(1L, "desc", List.of(photo1, photo2), JAKARTA_LAT, JAKARTA_LNG);
            var upload1 = UploadResponse.builder().id("cloud-id-1").url("http://cdn.example.com/photo1.jpg").createdAt(LocalDateTime.now()).build();
            var upload2 = UploadResponse.builder().id("cloud-id-2").url("http://cdn.example.com/photo2.jpg").createdAt(LocalDateTime.now()).build();

            when(categoryRepository.findById(1L)).thenReturn(Optional.of(category));
            when(uploadService.upload(photo1)).thenReturn(upload1);
            when(uploadService.upload(photo2)).thenReturn(upload2);
            when(reportRepository.save(any(Report.class))).thenThrow(new RuntimeException("db down"));

            assertThatThrownBy(() -> reportService.createReport(reporter, request))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessage("db down");

            verify(uploadService).delete("cloud-id-1");
            verify(uploadService).delete("cloud-id-2");
            verify(reportStatusRepository, never()).save(any());
            verify(reportPhotoRepository, never()).saveAll(any());
        }

        @Test
        @DisplayName("deletes earlier successful uploads and rethrows when a later upload fails")
        void createReportPartialUploadFailureDeletesEarlierUploads() {
            var reporter = buildUser(UUID.randomUUID(), UserRole.CITIZEN);
            var category = buildCategory(1L);
            var photo1 = mock(MultipartFile.class);
            var photo2 = mock(MultipartFile.class);
            var request = new CreateReportRequest(1L, "desc", List.of(photo1, photo2), JAKARTA_LAT, JAKARTA_LNG);
            var upload1 = UploadResponse.builder().id("cloud-id-1").url("http://cdn.example.com/photo1.jpg").createdAt(LocalDateTime.now()).build();

            when(categoryRepository.findById(1L)).thenReturn(Optional.of(category));
            when(uploadService.upload(photo1)).thenReturn(upload1);
            when(uploadService.upload(photo2)).thenThrow(new ValidationException("Only JPEG, PNG, WEBP, or GIF images are allowed."));

            assertThatThrownBy(() -> reportService.createReport(reporter, request))
                    .isInstanceOf(ValidationException.class);

            verify(uploadService).delete("cloud-id-1");
            verify(reportRepository, never()).save(any());
        }
    }

    // ==================================================================
    // getNearbyReports
    // ==================================================================
    @Nested
    @DisplayName("getNearbyReports")
    class GetNearbyReports {

        @Test
        @DisplayName("returns mapped list with the first photo as thumbnail")
        void getNearbyReportsSuccess() {
            var reporter = buildUser(UUID.randomUUID(), UserRole.CITIZEN);
            var category = buildCategory(1L);
            var report1 = buildReport(UUID.randomUUID(), reporter, category, ReportStatus.REPORTED, JAKARTA_LAT, JAKARTA_LNG);
            var report2 = buildReport(UUID.randomUUID(), reporter, category, ReportStatus.ACKNOWLEDGED, JAKARTA_LAT + 0.01, JAKARTA_LNG + 0.01);
            var photo1 = buildReportPhoto(report1, "http://cdn.example.com/p1.jpg", "p1", 0);
            var photo2 = buildReportPhoto(report2, "http://cdn.example.com/p2.jpg", "p2", 0);
            var response1 = NearbyReportResponse.builder().id(report1.getId()).build();
            var response2 = NearbyReportResponse.builder().id(report2.getId()).build();

            when(reportRepository.findNearbyReport(JAKARTA_LAT, JAKARTA_LNG, 1000, 20))
                    .thenReturn(List.of(report1, report2));
            when(reportPhotoRepository.findByReportIdInOrderByReportIdAscPositionAsc(List.of(report1.getId(), report2.getId())))
                    .thenReturn(List.of(photo1, photo2));
            when(reportMapper.toNearbyResponse(report1, "http://cdn.example.com/p1.jpg")).thenReturn(response1);
            when(reportMapper.toNearbyResponse(report2, "http://cdn.example.com/p2.jpg")).thenReturn(response2);

            var result = reportService.getNearbyReports(JAKARTA_LAT, JAKARTA_LNG, 1000, 20);

            assertThat(result).containsExactly(response1, response2);
        }

        @Test
        @DisplayName("passes a null thumbnail when a report has no photos")
        void getNearbyReportsNoPhotos() {
            var reporter = buildUser(UUID.randomUUID(), UserRole.CITIZEN);
            var category = buildCategory(1L);
            var report1 = buildReport(UUID.randomUUID(), reporter, category, ReportStatus.REPORTED, JAKARTA_LAT, JAKARTA_LNG);
            var response1 = NearbyReportResponse.builder().id(report1.getId()).build();

            when(reportRepository.findNearbyReport(JAKARTA_LAT, JAKARTA_LNG, 1000, 20))
                    .thenReturn(List.of(report1));
            when(reportPhotoRepository.findByReportIdInOrderByReportIdAscPositionAsc(List.of(report1.getId())))
                    .thenReturn(List.of());
            when(reportMapper.toNearbyResponse(report1, null)).thenReturn(response1);

            var result = reportService.getNearbyReports(JAKARTA_LAT, JAKARTA_LNG, 1000, 20);

            assertThat(result).containsExactly(response1);
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
        @DisplayName("returns mapped response with ordered photo urls when found")
        void getReportDetailSuccess() {
            var reporter = buildUser(UUID.randomUUID(), UserRole.CITIZEN);
            var category = buildCategory(1L);
            var report = buildReport(UUID.randomUUID(), reporter, category, ReportStatus.REPORTED, JAKARTA_LAT, JAKARTA_LNG);
            var photo1 = buildReportPhoto(report, "http://cdn.example.com/p1.jpg", "p1", 0);
            var photo2 = buildReportPhoto(report, "http://cdn.example.com/p2.jpg", "p2", 1);
            var expected = buildReportResponse(report, List.of(photo1.getPhotoUrl(), photo2.getPhotoUrl()));

            when(reportRepository.findById(report.getId())).thenReturn(Optional.of(report));
            when(reportPhotoRepository.findByReportIdOrderByPositionAsc(report.getId())).thenReturn(List.of(photo1, photo2));
            when(reportMapper.toResponse(report, List.of(photo1.getPhotoUrl(), photo2.getPhotoUrl()))).thenReturn(expected);

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
            var report = buildReport(id, reporter, category, ReportStatus.ACKNOWLEDGED, JAKARTA_LAT, JAKARTA_LNG);
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
        @DisplayName("builds an offset pageable, maps content with photo urls, and returns page metadata")
        void getMyReportsSuccess() {
            var reporter = buildUser(UUID.randomUUID(), UserRole.CITIZEN);
            var category = buildCategory(1L);
            var report1 = buildReport(UUID.randomUUID(), reporter, category, ReportStatus.REPORTED, JAKARTA_LAT, JAKARTA_LNG);
            var report2 = buildReport(UUID.randomUUID(), reporter, category, ReportStatus.RESOLVED, JAKARTA_LAT, JAKARTA_LNG);
            var photo1 = buildReportPhoto(report1, "http://cdn.example.com/p1.jpg", "p1", 0);
            var photo2 = buildReportPhoto(report2, "http://cdn.example.com/p2.jpg", "p2", 0);
            var response1 = buildReportResponse(report1, List.of(photo1.getPhotoUrl()));
            var response2 = buildReportResponse(report2, List.of(photo2.getPhotoUrl()));

            when(reportRepository.findByReporterId(eq(reporter.getId()), any(Pageable.class)))
                    .thenAnswer(invocation -> {
                        Pageable pageable = invocation.getArgument(1);
                        return new PageImpl<Report>(List.of(report1, report2), pageable, 5);
                    });
            when(reportPhotoRepository.findByReportIdInOrderByReportIdAscPositionAsc(List.of(report1.getId(), report2.getId())))
                    .thenReturn(List.of(photo1, photo2));
            when(reportMapper.toResponse(report1, List.of(photo1.getPhotoUrl()))).thenReturn(response1);
            when(reportMapper.toResponse(report2, List.of(photo2.getPhotoUrl()))).thenReturn(response2);

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
            var report = buildReport(UUID.randomUUID(), reporter, category, ReportStatus.REPORTED, JAKARTA_LAT, JAKARTA_LNG);
            var request = new UpdateStatusRequest(ReportStatus.ACKNOWLEDGED, null);
            var expected = buildReportResponse(report, List.of());

            when(reportRepository.findById(report.getId())).thenReturn(Optional.of(report));
            when(reportRepository.save(report)).thenReturn(report);
            when(reportPhotoRepository.findByReportIdOrderByPositionAsc(report.getId())).thenReturn(List.of());
            when(reportMapper.toResponse(report, List.of())).thenReturn(expected);

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
            var report = buildReport(UUID.randomUUID(), reporter, category, ReportStatus.REPORTED, JAKARTA_LAT, JAKARTA_LNG);
            var request = new UpdateStatusRequest(ReportStatus.REJECTED, "Duplicate report");
            var expected = buildReportResponse(report, List.of());

            when(reportRepository.findById(report.getId())).thenReturn(Optional.of(report));
            when(reportRepository.save(report)).thenReturn(report);
            when(reportPhotoRepository.findByReportIdOrderByPositionAsc(report.getId())).thenReturn(List.of());
            when(reportMapper.toResponse(report, List.of())).thenReturn(expected);

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
            var report = buildReport(UUID.randomUUID(), reporter, category, ReportStatus.REPORTED, JAKARTA_LAT, JAKARTA_LNG);
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
            var report = buildReport(UUID.randomUUID(), reporter, category, ReportStatus.REPORTED, JAKARTA_LAT, JAKARTA_LNG);
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
        @DisplayName("updates fields without touching photos when no new photos are provided")
        void updateReportSuccessNoPhoto() {
            var reporter = buildUser(UUID.randomUUID(), UserRole.CITIZEN);
            var oldCategory = buildCategory(1L);
            var newCategory = buildCategory(2L);
            var report = buildReport(UUID.randomUUID(), reporter, oldCategory, ReportStatus.REPORTED, JAKARTA_LAT, JAKARTA_LNG);
            var existingPhoto = buildReportPhoto(report, "http://cdn.example.com/old.jpg", "old-photo", 0);
            var request = new UpdateReportRequest(2L, "Updated description", null, JAKARTA_LAT + 0.1, JAKARTA_LNG + 0.1);
            var expected = buildReportResponse(report, List.of(existingPhoto.getPhotoUrl()));

            when(reportRepository.findById(report.getId())).thenReturn(Optional.of(report));
            when(categoryRepository.findById(2L)).thenReturn(Optional.of(newCategory));
            when(reportRepository.save(report)).thenReturn(report);
            when(reportPhotoRepository.findByReportIdOrderByPositionAsc(report.getId())).thenReturn(List.of(existingPhoto));
            when(reportMapper.toResponse(report, List.of(existingPhoto.getPhotoUrl()))).thenReturn(expected);

            var result = reportService.updateReport(report.getId(), reporter, request);

            assertThat(result).isEqualTo(expected);
            assertThat(report.getCategory()).isEqualTo(newCategory);
            assertThat(report.getDescription()).isEqualTo("Updated description");
            assertThat(report.getLatitude()).isEqualTo(JAKARTA_LAT + 0.1);
            assertThat(report.getLongitude()).isEqualTo(JAKARTA_LNG + 0.1);

            verify(uploadService, never()).upload(any());
            verify(uploadService, never()).delete(any());
            verify(reportPhotoRepository, never()).deleteAll(any());
            verify(reportPhotoRepository, never()).saveAll(any());
        }

        @Test
        @DisplayName("uploads new photos, replaces the old set, and deletes old photo assets on success")
        void updateReportSuccessWithPhotosReplacesOldSet() {
            var reporter = buildUser(UUID.randomUUID(), UserRole.CITIZEN);
            var category = buildCategory(1L);
            var report = buildReport(UUID.randomUUID(), reporter, category, ReportStatus.REPORTED, JAKARTA_LAT, JAKARTA_LNG);
            var oldPhoto = buildReportPhoto(report, "http://cdn.example.com/old.jpg", "old-photo", 0);
            var newFile = mock(MultipartFile.class);
            var request = new UpdateReportRequest(1L, "Updated description", List.of(newFile), JAKARTA_LAT, JAKARTA_LNG);
            var upload = UploadResponse.builder().id("new-photo").url("http://cdn.example.com/new.jpg").createdAt(LocalDateTime.now()).build();
            var expected = buildReportResponse(report, List.of(upload.url()));

            when(reportRepository.findById(report.getId())).thenReturn(Optional.of(report));
            when(categoryRepository.findById(1L)).thenReturn(Optional.of(category));
            when(uploadService.upload(newFile)).thenReturn(upload);
            when(reportRepository.save(report)).thenReturn(report);
            when(reportPhotoRepository.findByReportIdOrderByPositionAsc(report.getId())).thenReturn(List.of(oldPhoto));
            when(reportPhotoRepository.saveAll(anyList())).thenAnswer(inv -> inv.getArgument(0));
            when(reportMapper.toResponse(report, List.of(upload.url()))).thenReturn(expected);

            var result = reportService.updateReport(report.getId(), reporter, request);

            assertThat(result).isEqualTo(expected);

            verify(reportPhotoRepository).deleteAll(List.of(oldPhoto));
            verify(uploadService).delete("old-photo");
            verify(uploadService, never()).delete("new-photo");

            @SuppressWarnings("unchecked")
            var photosCaptor = ArgumentCaptor.forClass(List.class);
            verify(reportPhotoRepository).saveAll(photosCaptor.capture());
            List<ReportPhoto> savedPhotos = photosCaptor.getValue();
            assertThat(savedPhotos).hasSize(1);
            assertThat(savedPhotos.get(0).getPhotoUrl()).isEqualTo(upload.url());
            assertThat(savedPhotos.get(0).getPhotoPublicId()).isEqualTo(upload.id());
            assertThat(savedPhotos.get(0).getPosition()).isEqualTo(0);
        }

        @Test
        @DisplayName("does not delete any upload when the report previously had no photos")
        void updateReportSuccessWithPhotosNoOldUpload() {
            var reporter = buildUser(UUID.randomUUID(), UserRole.CITIZEN);
            var category = buildCategory(1L);
            var report = buildReport(UUID.randomUUID(), reporter, category, ReportStatus.REPORTED, JAKARTA_LAT, JAKARTA_LNG);
            var newFile = mock(MultipartFile.class);
            var request = new UpdateReportRequest(1L, "Updated description", List.of(newFile), JAKARTA_LAT, JAKARTA_LNG);
            var upload = UploadResponse.builder().id("new-photo").url("http://cdn.example.com/new.jpg").createdAt(LocalDateTime.now()).build();
            var expected = buildReportResponse(report, List.of(upload.url()));

            when(reportRepository.findById(report.getId())).thenReturn(Optional.of(report));
            when(categoryRepository.findById(1L)).thenReturn(Optional.of(category));
            when(uploadService.upload(newFile)).thenReturn(upload);
            when(reportRepository.save(report)).thenReturn(report);
            when(reportPhotoRepository.findByReportIdOrderByPositionAsc(report.getId())).thenReturn(List.of());
            when(reportPhotoRepository.saveAll(anyList())).thenAnswer(inv -> inv.getArgument(0));
            when(reportMapper.toResponse(report, List.of(upload.url()))).thenReturn(expected);

            var result = reportService.updateReport(report.getId(), reporter, request);

            assertThat(result).isEqualTo(expected);
            verify(uploadService, never()).delete(any());
        }

        @Test
        @DisplayName("deletes newly uploaded photos and rethrows when save fails, leaving old photos untouched")
        void updateReportSaveFailureDeletesNewUploads() {
            var reporter = buildUser(UUID.randomUUID(), UserRole.CITIZEN);
            var category = buildCategory(1L);
            var report = buildReport(UUID.randomUUID(), reporter, category, ReportStatus.REPORTED, JAKARTA_LAT, JAKARTA_LNG);
            var newFile = mock(MultipartFile.class);
            var request = new UpdateReportRequest(1L, "Updated description", List.of(newFile), JAKARTA_LAT, JAKARTA_LNG);
            var upload = UploadResponse.builder().id("new-photo").url("http://cdn.example.com/new.jpg").createdAt(LocalDateTime.now()).build();

            when(reportRepository.findById(report.getId())).thenReturn(Optional.of(report));
            when(categoryRepository.findById(1L)).thenReturn(Optional.of(category));
            when(uploadService.upload(newFile)).thenReturn(upload);
            when(reportRepository.save(report)).thenThrow(new RuntimeException("db down"));

            assertThatThrownBy(() -> reportService.updateReport(report.getId(), reporter, request))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessage("db down");

            verify(uploadService).delete("new-photo");
            verify(reportPhotoRepository, never()).deleteAll(any());
            verify(reportPhotoRepository, never()).saveAll(any());
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
            var report = buildReport(UUID.randomUUID(), reporter, category, ReportStatus.REPORTED, JAKARTA_LAT, JAKARTA_LNG);
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
            var report = buildReport(UUID.randomUUID(), reporter, category, ReportStatus.ACKNOWLEDGED, JAKARTA_LAT, JAKARTA_LNG);
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
            var report = buildReport(UUID.randomUUID(), reporter, category, ReportStatus.REPORTED, JAKARTA_LAT, JAKARTA_LNG);
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
            var report = buildReport(UUID.randomUUID(), reporter, category, ReportStatus.REPORTED, JAKARTA_LAT, JAKARTA_LNG);
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
        @DisplayName("deletes report and does not touch upload service when there are no photos")
        void deleteReportSuccessNoPhoto() {
            var reporter = buildUser(UUID.randomUUID(), UserRole.CITIZEN);
            var category = buildCategory(1L);
            var report = buildReport(UUID.randomUUID(), reporter, category, ReportStatus.REPORTED, JAKARTA_LAT, JAKARTA_LNG);

            when(reportRepository.findById(report.getId())).thenReturn(Optional.of(report));
            when(reportPhotoRepository.findByReportIdOrderByPositionAsc(report.getId())).thenReturn(List.of());

            reportService.deleteReport(report.getId(), reporter);

            verify(reportRepository).delete(report);
            verify(uploadService, never()).delete(any());
        }

        @Test
        @DisplayName("deletes the report_photos rows before the report row, so Hibernate never sees a dangling reference")
        void deleteReportDeletesPhotoRowsBeforeReportRow() {
            var reporter = buildUser(UUID.randomUUID(), UserRole.CITIZEN);
            var category = buildCategory(1L);
            var report = buildReport(UUID.randomUUID(), reporter, category, ReportStatus.REPORTED, JAKARTA_LAT, JAKARTA_LNG);
            var photo1 = buildReportPhoto(report, "http://cdn.example.com/p1.jpg", "photo-1", 0);

            when(reportRepository.findById(report.getId())).thenReturn(Optional.of(report));
            when(reportPhotoRepository.findByReportIdOrderByPositionAsc(report.getId())).thenReturn(List.of(photo1));

            reportService.deleteReport(report.getId(), reporter);

            var inOrder = org.mockito.Mockito.inOrder(reportPhotoRepository, reportRepository);
            inOrder.verify(reportPhotoRepository).deleteAll(List.of(photo1));
            inOrder.verify(reportRepository).delete(report);
        }

        @Test
        @DisplayName("deletes report and every associated upload asset when photos are present")
        void deleteReportSuccessWithPhotos() {
            var reporter = buildUser(UUID.randomUUID(), UserRole.CITIZEN);
            var category = buildCategory(1L);
            var report = buildReport(UUID.randomUUID(), reporter, category, ReportStatus.REPORTED, JAKARTA_LAT, JAKARTA_LNG);
            var photo1 = buildReportPhoto(report, "http://cdn.example.com/p1.jpg", "photo-1", 0);
            var photo2 = buildReportPhoto(report, "http://cdn.example.com/p2.jpg", "photo-2", 1);

            when(reportRepository.findById(report.getId())).thenReturn(Optional.of(report));
            when(reportPhotoRepository.findByReportIdOrderByPositionAsc(report.getId())).thenReturn(List.of(photo1, photo2));

            reportService.deleteReport(report.getId(), reporter);

            verify(reportRepository).delete(report);
            verify(uploadService).delete("photo-1");
            verify(uploadService).delete("photo-2");
        }

        @Test
        @DisplayName("swallows exceptions raised while deleting an upload asset")
        void deleteReportUploadDeleteFailureIsSwallowed() {
            var reporter = buildUser(UUID.randomUUID(), UserRole.CITIZEN);
            var category = buildCategory(1L);
            var report = buildReport(UUID.randomUUID(), reporter, category, ReportStatus.REPORTED, JAKARTA_LAT, JAKARTA_LNG);
            var photo = buildReportPhoto(report, "http://cdn.example.com/p1.jpg", "photo-1", 0);

            when(reportRepository.findById(report.getId())).thenReturn(Optional.of(report));
            when(reportPhotoRepository.findByReportIdOrderByPositionAsc(report.getId())).thenReturn(List.of(photo));
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
            var report = buildReport(UUID.randomUUID(), reporter, category, ReportStatus.REPORTED, JAKARTA_LAT, JAKARTA_LNG);

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
            var report = buildReport(UUID.randomUUID(), reporter, category, ReportStatus.RESOLVED, JAKARTA_LAT, JAKARTA_LNG);

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
        @DisplayName("returns queue items with computed distance, thumbnail and aggregated counts")
        void getQueueSuccess() {
            var reporter = buildUser(UUID.randomUUID(), UserRole.CITIZEN);
            var category = buildCategory(1L);
            double nearbyLat = JAKARTA_LAT + 0.001;
            double nearbyLng = JAKARTA_LNG + 0.001;
            var report1 = buildReport(UUID.randomUUID(), reporter, category, ReportStatus.REPORTED, nearbyLat, nearbyLng);
            var report2 = buildReport(UUID.randomUUID(), reporter, category, ReportStatus.ACKNOWLEDGED, JAKARTA_LAT, JAKARTA_LNG);
            var photo1 = buildReportPhoto(report1, "http://cdn.example.com/p1.jpg", "p1", 0);
            var photo2 = buildReportPhoto(report2, "http://cdn.example.com/p2.jpg", "p2", 0);

            var queueResponse1 = QueueReportResponse.builder()
                    .id(report1.getId())
                    .category(buildCategoryResponse(category))
                    .description(report1.getDescription())
                    .photoUrl(photo1.getPhotoUrl())
                    .status(report1.getStatus())
                    .latitude(report1.getLatitude())
                    .longitude(report1.getLongitude())
                    .createdAt(report1.getCreatedAt())
                    .build();
            var queueResponse2 = QueueReportResponse.builder()
                    .id(report2.getId())
                    .category(buildCategoryResponse(category))
                    .description(report2.getDescription())
                    .photoUrl(photo2.getPhotoUrl())
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
            when(reportPhotoRepository.findByReportIdInOrderByReportIdAscPositionAsc(List.of(report1.getId(), report2.getId())))
                    .thenReturn(List.of(photo1, photo2));
            when(reportMapper.toQueueResponse(report1, photo1.getPhotoUrl())).thenReturn(queueResponse1);
            when(reportMapper.toQueueResponse(report2, photo2.getPhotoUrl())).thenReturn(queueResponse2);

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
            assertThat(result.items().get(0).photoUrl()).isEqualTo(photo1.getPhotoUrl());
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
