package com.project.pantau.service.impl;

import com.project.pantau.common.exception.IllegalTransitionException;
import com.project.pantau.common.exception.ResourceNotFoundException;
import com.project.pantau.common.exception.ValidationException;
import com.project.pantau.common.pagination.OffsetPageable;
import com.project.pantau.common.response.PageMeta;
import com.project.pantau.common.response.PagedResponse;
import com.project.pantau.common.utils.GeoUtils;
import com.project.pantau.common.utils.ReportStatusTransitions;
import com.project.pantau.dto.report.*;
import com.project.pantau.dto.report_status.ReportStatusResponse;
import com.project.pantau.dto.upload.UploadResponse;
import com.project.pantau.entity.*;
import com.project.pantau.enums.QueueTab;
import com.project.pantau.enums.ReportStatus;
import com.project.pantau.mapper.ReportMapper;
import com.project.pantau.mapper.ReportStatusMapper;
import com.project.pantau.repository.CategoryRepository;
import com.project.pantau.repository.ReportPhotoRepository;
import com.project.pantau.repository.ReportRepository;
import com.project.pantau.repository.ReportStatusRepository;
import com.project.pantau.service.ReportService;
import com.project.pantau.service.UploadService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ReportServiceImpl implements ReportService {
    private static final Logger logger = LoggerFactory.getLogger(ReportServiceImpl.class);
    private static final int MAX_RADIUS_METERS = 50_000;
    private static final int MAX_NEARBY_LIMIT = 100;
    private static final int MAX_PAGE_SIZE = 100;

    private final ReportRepository reportRepository;
    private final ReportStatusRepository reportStatusRepository;
    private final ReportPhotoRepository reportPhotoRepository;
    private final CategoryRepository categoryRepository;
    private final UploadService uploadService;
    private final ReportMapper reportMapper;
    private final ReportStatusMapper reportStatusMapper;

    @Override
    public ReportResponse createReport(
            User reporter,
            CreateReportRequest request
    ) {
        validateCoordinates(request.latitude(), request.longitude());

        var category = categoryRepository.findById(request.categoryId())
                .orElseThrow(() -> new ResourceNotFoundException("Category not found with id: " + request.categoryId()));
        var uploads = uploadAll(request.photos());

        try {
            return saveReportAndHistory(
                    reporter,
                    request,
                    category,
                    uploads
            );
        } catch (RuntimeException e) {
            deleteUploads(uploads);
            throw e;
        }
    }

    @Override
    @Transactional(readOnly = true)
    public List<NearbyReportResponse> getNearbyReports(
            double latitude,
            double longitude,
            int radiusMeter,
            int limit
    ) {
        validateCoordinates(latitude, longitude);

        if (radiusMeter <= 0) {
            throw new ValidationException("Radius must be greater than 0");
        }
        if (radiusMeter > MAX_RADIUS_METERS) {
            throw new ValidationException("Radius must not exceed " + MAX_RADIUS_METERS + " meters");
        }
        if (limit <= 0) {
            throw new ValidationException("Limit must be greater than 0");
        }
        if (limit > MAX_NEARBY_LIMIT) {
            throw new ValidationException("Limit must not exceed " + MAX_NEARBY_LIMIT);
        }

        var reports = reportRepository.findNearbyReport(latitude, longitude, radiusMeter, limit);
        var photoUrlsByReport = loadPhotoUrlsByReport(reports);

        return reports.stream()
                .map(report -> reportMapper.toNearbyResponse(
                        report, photoUrlsByReport.getOrDefault(report.getId(), List.of())))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public ReportResponse getReportDetail(UUID id) {
        var report = findReportById(id);
        var photoUrls = photoUrls(reportPhotoRepository.findByReportIdOrderByPositionAsc(id));
        return reportMapper.toResponse(report, photoUrls);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ReportStatusResponse> getReportHistory(UUID id) {
        if (!reportRepository.existsById(id)) {
            throw new ResourceNotFoundException("Report not found with id: " + id);
        }

        return reportStatusMapper.toResponse(reportStatusRepository.findByReportIdOrderByCreatedAtAsc(id));
    }

    @Override
    @Transactional(readOnly = true)
    public PagedResponse<ReportResponse> getMyReports(
            User reporter,
            int limit,
            int offset
    ) {
        if (limit <= 0) {
            throw new ValidationException("Limit must be greater than 0");
        }
        if (limit > MAX_PAGE_SIZE) {
            throw new ValidationException("Limit must not exceed " + MAX_PAGE_SIZE);
        }
        if (offset < 0) {
            throw new ValidationException("Offset must not be negative");
        }

        var pageable = OffsetPageable.of(
                offset,
                limit,
                Sort.by(Sort.Direction.DESC, "createdAt")
        );
        var page = reportRepository.findByReporterId(reporter.getId(), pageable);
        var reports = page.getContent();
        var photoUrlsByReport = loadPhotoUrlsByReport(reports);
        var items = reports.stream()
                .map(report -> reportMapper.toResponse(report, photoUrlsByReport.getOrDefault(report.getId(), List.of())))
                .toList();

        return new PagedResponse<>(
                items,
                new PageMeta(
                        limit,
                        offset,
                        page.getTotalElements(),
                        page.hasNext()
                )
        );
    }

    @Override
    public ReportResponse updateReportStatus(
            UUID id,
            User resolver,
            UpdateStatusRequest request
    ) {
        var report = findReportById(id);
        var fromStatus = report.getStatus();
        var toStatus = request.toStatus();

        if (!ReportStatusTransitions.isAllowed(fromStatus, toStatus)) {
            throw new IllegalTransitionException("Cannot move a report from " + fromStatus + " to" + toStatus);
        }
        if (ReportStatusTransitions.requiresNote(toStatus) && (request.note() == null || request.note().isBlank())) {
            throw new ValidationException("A note is required when rejecting a report");
        }

        report.setStatus(toStatus);
        var savedReport = reportRepository.save(report);
        var reportStatusHistory = ReportStatusHistory.builder()
                .report(savedReport)
                .actor(resolver)
                .fromStatus(fromStatus)
                .toStatus(toStatus)
                .note(request.note())
                .build();

        reportStatusRepository.save(reportStatusHistory);

        var photoUrls = photoUrls(reportPhotoRepository.findByReportIdOrderByPositionAsc(id));
        return reportMapper.toResponse(savedReport, photoUrls);
    }

    @Override
    public ReportResponse updateReport(
            UUID id,
            User requester,
            UpdateReportRequest request
    ) {
        var report = findReportById(id);
        assertOwner(report, requester);
        assertEditable(report);

        validateCoordinates(request.latitude(), request.longitude());

        var category = categoryRepository.findById(request.categoryId())
                .orElseThrow(() -> new ResourceNotFoundException("Category not found with id: " + request.categoryId()));

        report.setCategory(category);
        report.setDescription(request.description());
        report.setLocation(GeoUtils.point(request.latitude(), request.longitude()));

        if (request.photos() != null && !request.photos().isEmpty()) {
            return updateReportWithNewPhotos(report, request.photos());
        }

        var saved = reportRepository.save(report);
        var photoUrls = photoUrls(reportPhotoRepository.findByReportIdOrderByPositionAsc(id));
        return reportMapper.toResponse(saved, photoUrls);
    }

    @Transactional
    private ReportResponse updateReportWithNewPhotos(Report report, List<MultipartFile> files) {
        var uploads = uploadAll(files);

        try {
            var saved = reportRepository.save(report);
            var oldPhotos = reportPhotoRepository.findByReportIdOrderByPositionAsc(saved.getId());
            reportPhotoRepository.deleteAll(oldPhotos);
            var newPhotos = savePhotos(saved, uploads);
            deletePhotoAssets(oldPhotos);

            return reportMapper.toResponse(saved, photoUrls(newPhotos));
        } catch (RuntimeException e) {
            deleteUploads(uploads);
            throw e;
        }
    }

    @Override
    @Transactional
    public void deleteReport(UUID id, User requester) {
        var report = findReportById(id);
        assertOwner(report, requester);
        assertEditable(report);

        var photos = reportPhotoRepository.findByReportIdOrderByPositionAsc(id);
        reportPhotoRepository.deleteAll(photos);
        reportRepository.delete(report);
        deletePhotoAssetsSwallowingErrors(photos, id);
    }

    private void deletePhotoAssetsSwallowingErrors(List<ReportPhoto> photos, UUID reportId) {
        for (var photo : photos) {
            if (photo.getPhotoPublicId() == null) {
                continue;
            }
            try {
                uploadService.delete(photo.getPhotoPublicId());
            } catch (RuntimeException e) {
                logger.warn("Failed to delete Cloudinary asset {} for deleted report {}",
                        photo.getPhotoPublicId(), reportId, e);
            }
        }
    }

    @Transactional
    private ReportResponse saveReportAndHistory(
            User reporter,
            CreateReportRequest request,
            Category category,
            List<UploadResponse> uploads
    ) {
        Report report = Report.builder()
                .reporter(reporter)
                .category(category)
                .description(request.description())
                .location(GeoUtils.point(request.latitude(), request.longitude()))
                .status(ReportStatus.REPORTED)
                .build();
        Report savedReport = reportRepository.save(report);
        var photos = savePhotos(savedReport, uploads);

        ReportStatusHistory reportStatusHistory = ReportStatusHistory.builder()
                .report(savedReport)
                .actor(reporter)
                .toStatus(ReportStatus.REPORTED)
                .build();

        reportStatusRepository.save(reportStatusHistory);

        return reportMapper.toResponse(savedReport, photoUrls(photos));
    }

    private List<UploadResponse> uploadAll(List<MultipartFile> files) {
        var uploads = new ArrayList<UploadResponse>();
        try {
            for (var file : files) {
                uploads.add(uploadService.upload(file));
            }
        } catch (RuntimeException e) {
            deleteUploads(uploads);
            throw e;
        }
        return uploads;
    }

    private void deleteUploads(List<UploadResponse> uploads) {
        uploads.forEach(upload -> uploadService.delete(upload.id()));
    }

    private void deletePhotoAssets(List<ReportPhoto> photos) {
        photos.forEach(photo -> {
            if (photo.getPhotoPublicId() != null) {
                uploadService.delete(photo.getPhotoPublicId());
            }
        });
    }

    private List<ReportPhoto> savePhotos(Report report, List<UploadResponse> uploads) {
        var photos = new ArrayList<ReportPhoto>();
        for (int i = 0; i < uploads.size(); i++) {
            var upload = uploads.get(i);
            photos.add(ReportPhoto.builder()
                    .report(report)
                    .photoUrl(upload.url())
                    .photoPublicId(upload.id())
                    .position(i)
                    .build());
        }
        return reportPhotoRepository.saveAll(photos);
    }

    private List<String> photoUrls(List<ReportPhoto> photos) {
        return photos.stream().map(ReportPhoto::getPhotoUrl).toList();
    }

    private Map<UUID, List<String>> loadPhotoUrlsByReport(List<Report> reports) {
        if (reports.isEmpty()) {
            return Map.of();
        }

        var reportIds = reports.stream().map(Report::getId).toList();
        return reportPhotoRepository.findByReportIdInOrderByReportIdAscPositionAsc(reportIds)
                .stream()
                .collect(Collectors.groupingBy(
                        photo -> photo.getReport().getId(),
                        LinkedHashMap::new,
                        Collectors.mapping(ReportPhoto::getPhotoUrl, Collectors.toList())
                ));
    }

    private Map<UUID, String> loadThumbnails(List<Report> reports) {
        var photoUrlsByReport = loadPhotoUrlsByReport(reports);
        var thumbnails = new HashMap<UUID, String>();
        reports.forEach(report -> {
            var urls = photoUrlsByReport.getOrDefault(report.getId(), List.of());
            thumbnails.put(report.getId(), urls.isEmpty() ? null : urls.getFirst());
        });
        return thumbnails;
    }

    private Report findReportById(UUID id) {
        return reportRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Report not found with id: " + id));
    }

    private void assertOwner(Report report, User requester) {
        if (!report.getReporter().getId().equals(requester.getId())) {
            throw new AccessDeniedException("You do not have permission to modify this report");
        }
    }

    private void assertEditable(Report report) {
        if (report.getStatus() != ReportStatus.REPORTED) {
            throw new IllegalTransitionException(
                    "Report can no longer be edited or deleted once a resolver has acted on it");
        }
    }

    @Override
    @Transactional(readOnly = true)
    public QueueResponse getQueue(
            QueueTab tab,
            double latitude,
            double longitude,
            int radiusMeter,
            int limit,
            int offset
    ) {
        validateCoordinates(latitude, longitude);

        if (radiusMeter <= 0) {
            throw new ValidationException("Radius must be greater than 0");
        }
        if (radiusMeter > MAX_RADIUS_METERS) {
            throw new ValidationException("Radius must not exceed " + MAX_RADIUS_METERS + " meters");
        }
        if (limit <= 0) {
            throw new ValidationException("Limit must be greater than 0");
        }
        if (limit > MAX_PAGE_SIZE) {
            throw new ValidationException("Limit must not exceed " + MAX_PAGE_SIZE);
        }
        if (offset < 0) {
            throw new ValidationException("Offset must not be negative");
        }

        var statuses = tab.statuses().stream().map(Enum::name).toList();
        var pageable = OffsetPageable.of(offset, limit, Sort.unsorted());
        var page = reportRepository.findQueueReports(statuses, latitude, longitude, radiusMeter, pageable);
        var reports = page.getContent();
        var thumbnails = loadThumbnails(reports);

        var items = reports.stream()
                .map(report -> {
                    var response = reportMapper.toQueueResponse(report, thumbnails.get(report.getId()));
                    var distance = GeoUtils.distanceMeters(
                            latitude, longitude, report.getLatitude(), report.getLongitude());
                    return new QueueReportResponse(
                            response.id(),
                            response.category(),
                            response.description(),
                            response.photoUrl(),
                            response.status(),
                            response.latitude(),
                            response.longitude(),
                            distance,
                            response.createdAt()
                    );
                })
                .toList();

        var counts = buildQueueCounts(reportRepository.countQueueReportsByStatus(latitude, longitude, radiusMeter));

        return new QueueResponse(
                items,
                new PageMeta(limit, offset, page.getTotalElements(), page.hasNext()),
                counts
        );
    }

    private QueueCounts buildQueueCounts(List<StatusCountProjection> rows) {
        long open = 0;
        long inProgress = 0;
        long resolved = 0;

        for (var row : rows) {
            switch (ReportStatus.valueOf(row.getStatus())) {
                case REPORTED, ACKNOWLEDGED -> open += row.getCount();
                case IN_PROGRESS -> inProgress += row.getCount();
                case RESOLVED -> resolved += row.getCount();
                default -> {
                }
            }
        }

        return new QueueCounts(open, inProgress, resolved);
    }

    private void validateCoordinates(double latitude, double longitude) {
        if (latitude < -90 || latitude > 90) {
            throw new ValidationException("Latitude must be between -90 and 90");
        }
        if (longitude < -180 || longitude > 180) {
            throw new ValidationException("Longitude must be between -180 and 180");
        }
    }
}
