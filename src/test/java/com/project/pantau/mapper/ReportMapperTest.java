package com.project.pantau.mapper;

import com.project.pantau.common.utils.GeoUtils;
import com.project.pantau.dto.report.NearbyReportResponse;
import com.project.pantau.dto.report.QueueReportResponse;
import com.project.pantau.dto.report.ReportResponse;
import com.project.pantau.entity.Category;
import com.project.pantau.entity.Report;
import com.project.pantau.entity.User;
import com.project.pantau.enums.ReportStatus;
import com.project.pantau.enums.UserRole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.offset;

/**
 * ReportMapper uses CategoryMapper (uses = CategoryMapper.class) which the
 * generated component-model=spring impl wires via an @Autowired field.
 * Mappers.getMapper() only does a plain no-arg instantiation, so we wire the
 * delegate in manually via reflection, mirroring what Spring would do.
 */
class ReportMapperTest {

    private static final double LATITUDE = -6.200000;
    private static final double LONGITUDE = 106.816666;

    private ReportMapper reportMapper;

    @BeforeEach
    void setUp() {
        ReportMapperImpl impl = new ReportMapperImpl();
        ReflectionTestUtils.setField(impl, "categoryMapper", new CategoryMapperImpl());
        reportMapper = impl;
    }

    private Report buildReport() {
        Category category = Category.builder()
                .id(10L)
                .name("Pothole")
                .slug("pothole")
                .isActive(true)
                .createdAt(LocalDateTime.now())
                .build();

        User reporter = User.builder()
                .id(UUID.randomUUID())
                .email("reporter@example.com")
                .password("secret")
                .displayName("Reporter")
                .role(UserRole.CITIZEN)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        return Report.builder()
                .id(UUID.randomUUID())
                .reporter(reporter)
                .category(category)
                .description("Big pothole on main road")
                .location(GeoUtils.point(LATITUDE, LONGITUDE))
                .status(ReportStatus.REPORTED)
                .createdAt(LocalDateTime.of(2026, 1, 1, 10, 0))
                .updatedAt(LocalDateTime.of(2026, 1, 2, 11, 0))
                .build();
    }

    @Test
    void toResponse_mapsAllFieldsIncludingLatLngNestedCategoryAndPhotoUrls() {
        Report report = buildReport();
        List<String> photoUrls = List.of("https://example.com/photo1.jpg", "https://example.com/photo2.jpg");

        ReportResponse response = reportMapper.toResponse(report, photoUrls);

        assertThat(response).isNotNull();
        assertThat(response.id()).isEqualTo(report.getId());
        assertThat(response.description()).isEqualTo("Big pothole on main road");
        assertThat(response.photoUrls()).containsExactly(
                "https://example.com/photo1.jpg", "https://example.com/photo2.jpg");
        assertThat(response.latitude()).isEqualTo(LATITUDE, offset(1e-9));
        assertThat(response.longitude()).isEqualTo(LONGITUDE, offset(1e-9));
        assertThat(response.status()).isEqualTo(ReportStatus.REPORTED);
        assertThat(response.createdAt()).isEqualTo(LocalDateTime.of(2026, 1, 1, 10, 0));
        assertThat(response.updatedAt()).isEqualTo(LocalDateTime.of(2026, 1, 2, 11, 0));

        assertThat(response.category()).isNotNull();
        assertThat(response.category().id()).isEqualTo(10L);
        assertThat(response.category().name()).isEqualTo("Pothole");
        assertThat(response.category().slug()).isEqualTo("pothole");
        assertThat(response.category().isActive()).isTrue();
    }

    @Test
    void toResponse_returnsNullWhenAllArgumentsNull() {
        assertThat(reportMapper.toResponse(null, null)).isNull();
    }

    @Test
    void toNearbyResponse_mapsAllFieldsIncludingDescription() {
        Report report = buildReport();
        List<String> photoUrls = List.of("https://example.com/photo1.jpg", "https://example.com/photo2.jpg");

        NearbyReportResponse response = reportMapper.toNearbyResponse(report, photoUrls);

        assertThat(response).isNotNull();
        assertThat(response.id()).isEqualTo(report.getId());
        assertThat(response.description()).isEqualTo("Big pothole on main road");
        assertThat(response.photoUrls()).containsExactly(
                "https://example.com/photo1.jpg", "https://example.com/photo2.jpg");
        assertThat(response.status()).isEqualTo(ReportStatus.REPORTED);
        assertThat(response.latitude()).isEqualTo(LATITUDE, offset(1e-9));
        assertThat(response.longitude()).isEqualTo(LONGITUDE, offset(1e-9));
        assertThat(response.createdAt()).isEqualTo(LocalDateTime.of(2026, 1, 1, 10, 0));

        assertThat(response.category()).isNotNull();
        assertThat(response.category().id()).isEqualTo(10L);
        assertThat(response.category().slug()).isEqualTo("pothole");
    }

    @Test
    void toNearbyResponse_returnsNullWhenAllArgumentsNull() {
        assertThat(reportMapper.toNearbyResponse(null, null)).isNull();
    }

    @Test
    void toQueueResponse_mapsFieldsAndLeavesDistanceMeterNull() {
        Report report = buildReport();

        QueueReportResponse response = reportMapper.toQueueResponse(report, "https://example.com/photo1.jpg");

        assertThat(response).isNotNull();
        assertThat(response.id()).isEqualTo(report.getId());
        assertThat(response.description()).isEqualTo("Big pothole on main road");
        assertThat(response.photoUrl()).isEqualTo("https://example.com/photo1.jpg");
        assertThat(response.status()).isEqualTo(ReportStatus.REPORTED);
        assertThat(response.latitude()).isEqualTo(LATITUDE, offset(1e-9));
        assertThat(response.longitude()).isEqualTo(LONGITUDE, offset(1e-9));
        assertThat(response.createdAt()).isEqualTo(LocalDateTime.of(2026, 1, 1, 10, 0));
        assertThat(response.distanceMeter()).isNull();

        assertThat(response.category()).isNotNull();
        assertThat(response.category().id()).isEqualTo(10L);
    }

    @Test
    void toQueueResponse_returnsNullWhenAllArgumentsNull() {
        assertThat(reportMapper.toQueueResponse(null, null)).isNull();
    }
}
