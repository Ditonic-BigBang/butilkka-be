package bigbang.butilkka_be.region;

import bigbang.butilkka_be.common.exception.AppException;
import bigbang.butilkka_be.region.dto.RegionRankingResponse;
import bigbang.butilkka_be.stats.DistrictStats;
import bigbang.butilkka_be.stats.DistrictStatsQueryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class RegionRankingServiceTest {

    @Mock
    private DistrictStatsQueryService districtStatsQueryService;

    private RegionRankingService service;

    @BeforeEach
    void setUp() {
        service = new RegionRankingService(districtStatsQueryService);
    }

    private static DistrictStats statsOf(String districtCode, String districtName, String grade, String direction, int year, int quarter, int rank) {
        DistrictStats stats = mock(DistrictStats.class);
        when(stats.getDistrictCode()).thenReturn(districtCode);
        when(stats.getDistrictName()).thenReturn(districtName);
        when(stats.getDeclineGrade()).thenReturn(grade);
        when(stats.getDirection()).thenReturn(direction);
        when(stats.getYear()).thenReturn(year);
        when(stats.getQuarter()).thenReturn(quarter);
        when(stats.getDistrictRank()).thenReturn(rank);
        return stats;
    }

    @Test
    void getRanking_withOrderTop_sortsByRankAscending() {
        DistrictStats a = statsOf("11110", "종로구", "A", "성장", 2026, 4, 1);
        DistrictStats b = statsOf("11140", "중구", "E", "쇠퇴", 2026, 4, 2);
        when(districtStatsQueryService.latestPerDistrict()).thenReturn(List.of(b, a));
        when(districtStatsQueryService.getLatestQuarterLabel()).thenReturn("2026Q4");

        RegionRankingResponse response = service.getRanking("top", null);

        assertThat(response.regions().get(0).regionCode()).isEqualTo("11110");
        assertThat(response.regions().get(0).regionName()).isEqualTo("종로구");
        assertThat(response.regions().get(0).rank()).isEqualTo(1);
        assertThat(response.regions().get(0).direction()).isEqualTo("UP");
    }

    @Test
    void getRanking_withOrderBottom_sortsByRankDescending() {
        DistrictStats a = statsOf("11110", "종로구", "A", "성장", 2026, 4, 1);
        DistrictStats b = statsOf("11140", "중구", "E", "쇠퇴", 2026, 4, 25);
        when(districtStatsQueryService.latestPerDistrict()).thenReturn(List.of(a, b));
        when(districtStatsQueryService.getLatestQuarterLabel()).thenReturn("2026Q4");

        RegionRankingResponse response = service.getRanking("bottom", null);

        assertThat(response.regions().get(0).regionCode()).isEqualTo("11140");
        assertThat(response.regions().get(0).regionName()).isEqualTo("중구");
        assertThat(response.regions().get(0).rank()).isEqualTo(25);
    }

    @Test
    void getRanking_withInvalidOrder_throwsBadRequest() {
        assertThatThrownBy(() -> service.getRanking("sideways", null))
                .isInstanceOf(AppException.class)
                .extracting(e -> ((AppException) e).getHttpStatus())
                .isEqualTo(org.springframework.http.HttpStatus.BAD_REQUEST);
    }

    @Test
    void getRanking_withQuarterParam_usesSpecificQuarter() {
        DistrictStats stats = statsOf("11110", "종로구", "B", "유지", 2025, 3, 5);
        when(districtStatsQueryService.forQuarter(2025, 3)).thenReturn(List.of(stats));

        RegionRankingResponse response = service.getRanking("top", "2025Q3");

        assertThat(response.quarter()).isEqualTo("2025Q3");
        assertThat(response.regions()).hasSize(1);
        assertThat(response.regions().get(0).direction()).isEqualTo("FLAT");
    }

    @Test
    void getRanking_withNullDirection_returnsFLAT() {
        DistrictStats stats = statsOf("11110", "종로구", "C", null, 2026, 4, 10);
        when(districtStatsQueryService.latestPerDistrict()).thenReturn(List.of(stats));
        when(districtStatsQueryService.getLatestQuarterLabel()).thenReturn("2026Q4");

        RegionRankingResponse response = service.getRanking("top", null);

        assertThat(response.regions().get(0).direction()).isEqualTo("FLAT");
    }
}
