package com.project.pantau.repository;

import com.project.pantau.entity.ReportPhoto;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ReportPhotoRepository extends JpaRepository<ReportPhoto, UUID> {
    List<ReportPhoto> findByReportIdOrderByPositionAsc(UUID reportId);

    List<ReportPhoto> findByReportIdInOrderByReportIdAscPositionAsc(List<UUID> reportIds);
}
