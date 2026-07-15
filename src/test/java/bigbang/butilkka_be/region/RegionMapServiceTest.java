package bigbang.butilkka_be.region;

import bigbang.butilkka_be.common.exception.AppException;
import bigbang.butilkka_be.region.dto.RegionMapResponse;
import bigbang.butilkka_be.stats.DistrictStats;
import bigbang.butilkka_be.stats.DistrictStatsQueryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RegionMapServiceTest {

    @Mock
    private DistrictStatsQueryService districtStatsQueryService;

    private RegionMapService service;

    @BeforeEach
    void setUp() {
        service = new RegionMapService(districtStatsQueryService);
    }

    @Test
    void getMap_withoutQuarter_usesLatestPerDistrict() {
        DistrictStats stats = mock(DistrictStats.class);
        when(stats.getDistrictCode()).thenReturn("11680");
        when(stats.getDistrictName()).thenReturn("강남구");
        when(stats.getDeclineGrade()).thenReturn("A");
        when(districtStatsQueryService.latestPerDistrict()).thenReturn(List.of(stats));
        when(districtStatsQueryService.getLatestQuarterLabel()).thenReturn("2026Q4");

        RegionMapResponse response = service.getMap(null);

        assertThat(response.quarter()).isEqualTo("2026Q4");
        assertThat(response.regions()).hasSize(1);
        assertThat(response.regions().get(0).regionCode()).isEqualTo("11680");
        assertThat(response.regions().get(0).regionName()).isEqualTo("강남구");
        assertThat(response.regions().get(0).district()).isEqualTo("강남구");
        assertThat(response.regions().get(0).grade()).isEqualTo("A");
    }

    @Test
    void getMap_withQuarter_usesForQuarter() {
        DistrictStats stats = mock(DistrictStats.class);
        when(stats.getDistrictCode()).thenReturn("11110");
        when(stats.getDistrictName()).thenReturn("종로구");
        when(stats.getDeclineGrade()).thenReturn("C");
        when(districtStatsQueryService.forQuarter(2025, 3)).thenReturn(List.of(stats));

        RegionMapResponse response = service.getMap("2025Q3");

        assertThat(response.quarter()).isEqualTo("2025Q3");
        assertThat(response.regions()).hasSize(1);
        assertThat(response.regions().get(0).regionCode()).isEqualTo("11110");
        assertThat(response.regions().get(0).grade()).isEqualTo("C");
    }

    @Test
    void getMap_withInvalidQuarter_throwsBadRequest() {
        assertThatThrownBy(() -> service.getMap("invalid"))
                .isInstanceOf(AppException.class)
                .extracting(e -> ((AppException) e).getHttpStatus())
                .isEqualTo(org.springframework.http.HttpStatus.BAD_REQUEST);
    }
}
