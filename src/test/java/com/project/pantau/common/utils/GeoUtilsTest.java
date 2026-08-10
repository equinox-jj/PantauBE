package com.project.pantau.common.utils;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Point;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class GeoUtilsTest {

    private static final double EARTH_RADIUS_METERS = 6_371_000;

    @Test
    @DisplayName("distanceMeters returns 0 for the same point")
    void distanceMeters_samePoint_isZero() {
        double distance = GeoUtils.distanceMeters(10.5, 20.5, 10.5, 20.5);

        assertThat(distance).isEqualTo(0.0);
    }

    @Test
    @DisplayName("distanceMeters for 1 degree of longitude along the equator equals R * (pi/180)")
    void distanceMeters_oneDegreeLongitudeAtEquator() {
        // On the equator, deltaLat = 0 so the haversine formula reduces to
        // a = sin^2(deltaLng/2), c = deltaLng (in radians) exactly, giving
        // distance = EARTH_RADIUS_METERS * radians(1).
        double expected = EARTH_RADIUS_METERS * Math.toRadians(1);

        double distance = GeoUtils.distanceMeters(0, 0, 0, 1);

        assertThat(distance).isCloseTo(expected, within(1e-6));
        assertThat(distance).isCloseTo(111_194.92664455874, within(1e-6));
    }

    @Test
    @DisplayName("distanceMeters for 1 degree of latitude equals the same great-circle length as 1 degree of longitude at the equator")
    void distanceMeters_oneDegreeLatitude() {
        double expected = EARTH_RADIUS_METERS * Math.toRadians(1);

        double distance = GeoUtils.distanceMeters(0, 0, 1, 0);

        assertThat(distance).isCloseTo(expected, within(1e-6));
    }

    @Test
    @DisplayName("distanceMeters is symmetric regardless of argument order")
    void distanceMeters_isSymmetric() {
        double forward = GeoUtils.distanceMeters(-6.2, 106.8, -6.9, 107.6);
        double backward = GeoUtils.distanceMeters(-6.9, 107.6, -6.2, 106.8);

        assertThat(forward).isEqualTo(backward);
    }

    @Test
    @DisplayName("distanceMeters matches an independently computed haversine value for two nearby coordinates")
    void distanceMeters_knownPair() {
        double distance = GeoUtils.distanceMeters(-6.2000, 106.8000, -6.9000, 107.6000);

        assertThat(distance).isCloseTo(117_765.03484378643, within(1e-6));
    }

    @Test
    @DisplayName("distanceMeters matches an independently computed haversine value across a long antipodal-ish route")
    void distanceMeters_longRoute() {
        double distance = GeoUtils.distanceMeters(-33.8688, 151.2093, 40.7128, -74.0060);

        assertThat(distance).isCloseTo(15_988_755.50703963, within(1e-3));
    }

    @Test
    @DisplayName("point creates a JTS point with x = longitude and y = latitude")
    void point_createsPointWithCorrectAxes() {
        Point point = GeoUtils.point(-6.1754, 106.8272);

        assertThat(point.getY()).isEqualTo(-6.1754);
        assertThat(point.getX()).isEqualTo(106.8272);
    }

    @Test
    @DisplayName("point sets the SRID to WGS84 (4326)")
    void point_setsSrid() {
        Point point = GeoUtils.point(-6.1754, 106.8272);

        assertThat(point.getSRID()).isEqualTo(4326);
    }

    @Test
    @DisplayName("latitude returns the point's Y ordinate")
    void latitude_returnsY() {
        Point point = GeoUtils.point(12.34, 56.78);

        assertThat(GeoUtils.latitude(point)).isEqualTo(12.34);
    }

    @Test
    @DisplayName("longitude returns the point's X ordinate")
    void longitude_returnsX() {
        Point point = GeoUtils.point(12.34, 56.78);

        assertThat(GeoUtils.longitude(point)).isEqualTo(56.78);
    }

    @Test
    @DisplayName("point -> latitude/longitude round-trips the original coordinates")
    void pointRoundTrip() {
        double lat = -6.9175;
        double lng = 107.6191;

        Point point = GeoUtils.point(lat, lng);

        assertThat(GeoUtils.latitude(point)).isEqualTo(lat);
        assertThat(GeoUtils.longitude(point)).isEqualTo(lng);
    }
}
