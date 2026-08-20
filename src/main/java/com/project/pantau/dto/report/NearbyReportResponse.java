package com.project.pantau.dto.report;

import com.project.pantau.dto.category.CategoryResponse;
import com.project.pantau.enums.ReportStatus;
import lombok.Builder;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Builder
public record NearbyReportResponse(
        UUID id,
        CategoryResponse category,
        String description,
        List<String> photoUrls,
        ReportStatus status,
        double latitude,
        double longitude,
        LocalDateTime createdAt
) {
}
