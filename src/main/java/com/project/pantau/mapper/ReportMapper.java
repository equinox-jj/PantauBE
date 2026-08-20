package com.project.pantau.mapper;

import com.project.pantau.dto.report.NearbyReportResponse;
import com.project.pantau.dto.report.QueueReportResponse;
import com.project.pantau.dto.report.ReportResponse;
import com.project.pantau.entity.Report;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

@Mapper(
        config = MapStructConfig.class,
        uses = CategoryMapper.class
)
public interface ReportMapper {
    @Mapping(target = "latitude", source = "report.latitude")
    @Mapping(target = "longitude", source = "report.longitude")
    @Mapping(target = "photoUrls", source = "photoUrls")
    ReportResponse toResponse(Report report, List<String> photoUrls);

    @Mapping(target = "latitude", source = "report.latitude")
    @Mapping(target = "longitude", source = "report.longitude")
    @Mapping(target = "photoUrls", source = "photoUrls")
    NearbyReportResponse toNearbyResponse(Report report, List<String> photoUrls);

    @Mapping(target = "latitude", source = "report.latitude")
    @Mapping(target = "longitude", source = "report.longitude")
    @Mapping(target = "photoUrl", source = "photoUrl")
    @Mapping(target = "distanceMeter", ignore = true)
    QueueReportResponse toQueueResponse(Report report, String photoUrl);
}
