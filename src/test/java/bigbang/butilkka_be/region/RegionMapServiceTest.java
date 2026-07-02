package bigbang.butilkka_be.region;

import bigbang.butilkka_be.common.exception.AppException;
import bigbang.butilkka_be.region.dto.RegionMapResponse;
import bigbang.butilkka_be.stats.CommercialStats;
import bigbang.butilkka_be.stats.CommercialStatsQueryService;
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
    private CommercialStatsQueryService commercialStatsQueryService;
    @Mock
    private RegionRepository regionRepository;
    @Mock
    private DistrictRepository districtRepository;

    private RegionMapService service;

    @org.junit.jupiter.api.BeforeEach
    void setUp() {
        service = new RegionMapService(commercialStatsQueryService, regionRepository, districtRepository);
    }

    @Test
    void getMap_withoutQuarter_usesLatestPerRegionAndResolvesRegionAndDistrict() {
        CommercialStats stats = mock(CommercialStats.class);
        when(stats.getRegionCode()).thenReturn("1168064000");
        when(stats.getYear()).thenReturn(2026);
        when(stats.getQuarter()).thenReturn(4);
        when(stats.getDeclineGrade()).thenReturn("A");
        when(commercialStatsQueryService.latestPerRegion()).thenReturn(List.of(stats));

        Region region = mock(Region.class);
        when(region.getRegionCode()).thenReturn("1168064000");
        when(region.getRegionName()).thenReturn("역삼1동");
        when(region.getDistrictCode()).thenReturn("11680");
        when(regionRepository.findById("1168064000")).thenReturn(java.util.Optional.of(region));

        District district = mock(District.class);
        when(district.getDistrictName()).thenReturn("강남구");
        when(districtRepository.findById("11680")).thenReturn(java.util.Optional.of(district));

        RegionMapResponse response = service.getMap(null);

        assertThat(response.quarter()).isEqualTo("2026Q4");
        assertThat(response.regions()).hasSize(1);
        assertThat(response.regions().get(0).regionCode()).isEqualTo("1168064000");
        assertThat(response.regions().get(0).regionName()).isEqualTo("역삼1동");
        assertThat(response.regions().get(0).district()).isEqualTo("강남구");
        assertThat(response.regions().get(0).grade()).isEqualTo("A");
    }

    @Test
    void getMap_withInvalidQuarter_throwsBadRequest() {
        assertThatThrownBy(() -> service.getMap("invalid"))
                .isInstanceOf(AppException.class)
                .extracting(e -> ((AppException) e).getHttpStatus())
                .isEqualTo(org.springframework.http.HttpStatus.BAD_REQUEST);
    }
}
