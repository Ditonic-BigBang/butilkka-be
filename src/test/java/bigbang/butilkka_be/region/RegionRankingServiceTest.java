package bigbang.butilkka_be.region;

import bigbang.butilkka_be.common.exception.AppException;
import bigbang.butilkka_be.region.dto.RegionRankingResponse;
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
class RegionRankingServiceTest {

    @Mock
    private CommercialStatsQueryService commercialStatsQueryService;
    @Mock
    private RegionRepository regionRepository;

    private RegionRankingService service;

    @org.junit.jupiter.api.BeforeEach
    void setUp() {
        service = new RegionRankingService(commercialStatsQueryService, regionRepository);
    }

    private static CommercialStats statsOf(String regionCode, String grade, int year, int quarter) {
        CommercialStats stats = mock(CommercialStats.class);
        when(stats.getRegionCode()).thenReturn(regionCode);
        when(stats.getDeclineGrade()).thenReturn(grade);
        when(stats.getYear()).thenReturn(year);
        when(stats.getQuarter()).thenReturn(quarter);
        return stats;
    }

    @Test
    void getRanking_withOrderTop_sortsBestGradeFirst() {
        CommercialStats a = statsOf("A", "A", 2026, 4);
        CommercialStats b = statsOf("B", "E", 2026, 4);
        when(commercialStatsQueryService.latestPerRegion()).thenReturn(List.of(b, a));
        when(commercialStatsQueryService.historyForRegion("A")).thenReturn(List.of(a));
        when(commercialStatsQueryService.historyForRegion("B")).thenReturn(List.of(b));

        Region regionA = mock(Region.class);
        when(regionA.getRegionName()).thenReturn("A동");
        when(regionRepository.findById("A")).thenReturn(java.util.Optional.of(regionA));
        Region regionB = mock(Region.class);
        when(regionB.getRegionName()).thenReturn("B동");
        when(regionRepository.findById("B")).thenReturn(java.util.Optional.of(regionB));

        RegionRankingResponse response = service.getRanking("top", null);

        assertThat(response.regions().get(0).regionCode()).isEqualTo("A");
        assertThat(response.regions().get(0).rank()).isEqualTo(1);
        assertThat(response.regions().get(0).direction()).isEqualTo("FLAT");
    }

    @Test
    void getRanking_withInvalidOrder_throwsBadRequest() {
        assertThatThrownBy(() -> service.getRanking("sideways", null))
                .isInstanceOf(AppException.class)
                .extracting(e -> ((AppException) e).getHttpStatus())
                .isEqualTo(org.springframework.http.HttpStatus.BAD_REQUEST);
    }
}
