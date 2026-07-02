package bigbang.butilkka_be.region;

import bigbang.butilkka_be.common.exception.AppException;
import bigbang.butilkka_be.lookup.RegionLookupService;
import bigbang.butilkka_be.lookup.model.GeoJsonFeature;
import bigbang.butilkka_be.region.dto.RegionLookupCandidate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Polygon;
import org.locationtech.jts.geom.PrecisionModel;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RegionsLookupServiceTest {

    private static final GeometryFactory GEOMETRY_FACTORY =
            new GeometryFactory(new PrecisionModel(), 4326);

    @Mock
    private RegionLookupService regionLookupService;

    private RegionsLookupService service;

    @BeforeEach
    void setUp() {
        service = new RegionsLookupService(regionLookupService);
    }

    private static Polygon square() {
        return GEOMETRY_FACTORY.createPolygon(new Coordinate[]{
                new Coordinate(126.9, 37.5),
                new Coordinate(127.0, 37.5),
                new Coordinate(127.0, 37.55),
                new Coordinate(126.9, 37.55),
                new Coordinate(126.9, 37.5)
        });
    }

    @Test
    void lookup_withBothKeywordAndCoordinate_throwsBadRequest() {
        assertThatThrownBy(() -> service.lookup("역삼", 37.5, 127.0))
                .isInstanceOf(AppException.class)
                .extracting(e -> ((AppException) e).getHttpStatus())
                .isEqualTo(org.springframework.http.HttpStatus.BAD_REQUEST);
    }

    @Test
    void lookup_withNeitherKeywordNorCoordinate_throwsBadRequest() {
        assertThatThrownBy(() -> service.lookup(null, null, null))
                .isInstanceOf(AppException.class)
                .extracting(e -> ((AppException) e).getHttpStatus())
                .isEqualTo(org.springframework.http.HttpStatus.BAD_REQUEST);
    }

    @Test
    void lookup_byKeyword_returnsMatchingCandidates() {
        GeoJsonFeature feature = new GeoJsonFeature(
                "1168064000", "서울특별시 강남구 역삼1동", "11680", "강남구", square());
        when(regionLookupService.searchByKeyword("역삼")).thenReturn(List.of(feature));

        List<RegionLookupCandidate> result = service.lookup("역삼", null, null);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).regionCode()).isEqualTo("1168064000");
        assertThat(result.get(0).regionName()).isEqualTo("역삼1동");
        assertThat(result.get(0).address()).isEqualTo("서울특별시 강남구 역삼1동");
    }

    @Test
    void lookup_byCoordinate_returnsMatchingCandidate() {
        GeoJsonFeature feature = new GeoJsonFeature(
                "1168064000", "서울특별시 강남구 역삼1동", "11680", "강남구", square());
        when(regionLookupService.findFeatureByCoordinate(37.5, 126.95)).thenReturn(Optional.of(feature));

        List<RegionLookupCandidate> result = service.lookup(null, 37.5, 126.95);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).regionCode()).isEqualTo("1168064000");
    }

    @Test
    void lookup_withNoMatch_throwsNotFound() {
        when(regionLookupService.searchByKeyword("존재하지않음")).thenReturn(List.of());

        assertThatThrownBy(() -> service.lookup("존재하지않음", null, null))
                .isInstanceOf(AppException.class)
                .extracting(e -> ((AppException) e).getHttpStatus())
                .isEqualTo(org.springframework.http.HttpStatus.NOT_FOUND);
    }
}
