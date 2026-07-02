package bigbang.butilkka_be.lookup;

import bigbang.butilkka_be.common.exception.AppException;
import bigbang.butilkka_be.lookup.dto.LookupResponse;
import bigbang.butilkka_be.lookup.model.GeoJsonFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.MultiPolygon;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.Polygon;
import org.locationtech.jts.geom.PrecisionModel;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RegionLookupServiceTest {

    private static final GeometryFactory GEOMETRY_FACTORY =
            new GeometryFactory(new PrecisionModel(), 4326);

    private final RegionLookupService service = new RegionLookupService();

    @Test
    void lookup_matchesPointExactlyOnDongBoundary() {
        Polygon square = GEOMETRY_FACTORY.createPolygon(new Coordinate[]{
                new Coordinate(126.9, 37.5),
                new Coordinate(127.0, 37.5),
                new Coordinate(127.0, 37.55),
                new Coordinate(126.9, 37.55),
                new Coordinate(126.9, 37.5)
        });
        GeoJsonFeature feature = new GeoJsonFeature(
                "1111000000", "서울특별시 테스트구 테스트동", "11110", "테스트구", square);
        ReflectionTestUtils.setField(service, "seoulFeatures", List.of(feature));

        LookupResponse response = service.lookup(37.5, 126.95);

        assertThat(response.regionName()).isEqualTo("테스트동");
    }

    @Test
    void lookup_withNaNLatitude_throwsBadRequest() {
        assertThatThrownBy(() -> service.lookup(Double.NaN, 126.95))
                .isInstanceOf(AppException.class)
                .extracting(e -> ((AppException) e).getHttpStatus())
                .isEqualTo(org.springframework.http.HttpStatus.BAD_REQUEST);
    }

    @Test
    void lookup_withInfiniteLongitude_throwsBadRequest() {
        assertThatThrownBy(() -> service.lookup(37.5, Double.POSITIVE_INFINITY))
                .isInstanceOf(AppException.class)
                .extracting(e -> ((AppException) e).getHttpStatus())
                .isEqualTo(org.springframework.http.HttpStatus.BAD_REQUEST);
    }

    @Test
    void lookup_withCoordinateOutsideSeoul_throwsBadRequest() {
        assertThatThrownBy(() -> service.lookup(35.1796, 129.0756))
                .isInstanceOf(AppException.class)
                .extracting(e -> ((AppException) e).getHttpStatus())
                .isEqualTo(org.springframework.http.HttpStatus.BAD_REQUEST);
    }

    @Test
    void lookup_withNoMatchingFeature_throwsNotFound() {
        ReflectionTestUtils.setField(service, "seoulFeatures", List.<GeoJsonFeature>of());

        assertThatThrownBy(() -> service.lookup(37.5665, 126.9780))
                .isInstanceOf(AppException.class)
                .extracting(e -> ((AppException) e).getHttpStatus())
                .isEqualTo(org.springframework.http.HttpStatus.NOT_FOUND);
    }

    @Test
    void parseGeometry_keepsHoleForPolygon() throws Exception {
        JsonNode geometryNode = new ObjectMapper().readTree("""
                {
                  "type": "Polygon",
                  "coordinates": [
                    [[0,0],[10,0],[10,10],[0,10],[0,0]],
                    [[4,4],[6,4],[6,6],[4,6],[4,4]]
                  ]
                }
                """);

        Geometry geometry = (Geometry) ReflectionTestUtils.invokeMethod(service, "parseGeometry", geometryNode);

        Polygon polygon = (Polygon) geometry;
        assertThat(polygon.getNumInteriorRing()).isEqualTo(1);
        assertThat(polygon.covers(point(5, 5))).isFalse();
        assertThat(polygon.covers(point(1, 1))).isTrue();
    }

    @Test
    void parseGeometry_keepsHoleForMultiPolygon() throws Exception {
        JsonNode geometryNode = new ObjectMapper().readTree("""
                {
                  "type": "MultiPolygon",
                  "coordinates": [
                    [
                      [[0,0],[10,0],[10,10],[0,10],[0,0]],
                      [[4,4],[6,4],[6,6],[4,6],[4,4]]
                    ]
                  ]
                }
                """);

        Geometry geometry = (Geometry) ReflectionTestUtils.invokeMethod(service, "parseGeometry", geometryNode);

        MultiPolygon multiPolygon = (MultiPolygon) geometry;
        Polygon polygon = (Polygon) multiPolygon.getGeometryN(0);
        assertThat(polygon.getNumInteriorRing()).isEqualTo(1);
        assertThat(multiPolygon.covers(point(5, 5))).isFalse();
        assertThat(multiPolygon.covers(point(1, 1))).isTrue();
    }

    @Test
    void searchByKeyword_returnsMatchingFeatures() {
        GeoJsonFeature feature = new GeoJsonFeature(
                "1111000000", "서울특별시 테스트구 테스트동", "11110", "테스트구", point(127.0, 37.5));
        ReflectionTestUtils.setField(service, "seoulFeatures", List.of(feature));

        List<GeoJsonFeature> results = service.searchByKeyword("테스트");

        assertThat(results).containsExactly(feature);
    }

    @Test
    void searchByKeyword_withNoMatch_returnsEmptyList() {
        ReflectionTestUtils.setField(service, "seoulFeatures", List.<GeoJsonFeature>of());

        List<GeoJsonFeature> results = service.searchByKeyword("존재하지않음");

        assertThat(results).isEmpty();
    }

    @Test
    void findFeatureByCoordinate_withNoMatch_returnsEmptyOptional() {
        ReflectionTestUtils.setField(service, "seoulFeatures", List.<GeoJsonFeature>of());

        java.util.Optional<GeoJsonFeature> result = service.findFeatureByCoordinate(37.5665, 126.9780);

        assertThat(result).isEmpty();
    }

    private static Point point(double x, double y) {
        return GEOMETRY_FACTORY.createPoint(new Coordinate(x, y));
    }
}
