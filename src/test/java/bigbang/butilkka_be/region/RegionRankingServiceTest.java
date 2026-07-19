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

import java.math.BigDecimal;
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

    private static DistrictStats statsOf(String districtCode, String districtName, String grade, String direction, BigDecimal compositeScore) {
        DistrictStats stats = mock(DistrictStats.class);
        when(stats.getDistrictCode()).thenReturn(districtCode);
        when(stats.getDistrictName()).thenReturn(districtName);
        when(stats.getDeclineGrade()).thenReturn(grade);
        when(stats.getDirection()).thenReturn(direction);
        when(stats.getCompositeScore()).thenReturn(compositeScore);
        return stats;
    }

    @Test
    void getRanking_withOrderTop_sortsByGradeDescThenScoreDesc() {
        // E등급(점수 높은 순) → D등급 → ... → A등급
        DistrictStats e1 = statsOf("11001", "E구1", "E", "쇠퇴", new BigDecimal("0.40"));
        DistrictStats e2 = statsOf("11002", "E구2", "E", "쇠퇴", new BigDecimal("0.35"));
        DistrictStats d1 = statsOf("11003", "D구", "D", "쇠퇴", new BigDecimal("0.38"));
        DistrictStats a1 = statsOf("11004", "A구", "A", "성장", new BigDecimal("0.30"));
        when(districtStatsQueryService.latestPerDistrict()).thenReturn(List.of(a1, d1, e2, e1));
        when(districtStatsQueryService.getLatestQuarterLabel()).thenReturn("2026Q4");

        RegionRankingResponse response = service.getRanking("top", null);

        assertThat(response.regions()).hasSize(4);
        assertThat(response.regions().get(0).regionCode()).isEqualTo("11001"); // E, 0.40
        assertThat(response.regions().get(1).regionCode()).isEqualTo("11002"); // E, 0.35
        assertThat(response.regions().get(2).regionCode()).isEqualTo("11003"); // D
        assertThat(response.regions().get(3).regionCode()).isEqualTo("11004"); // A
        assertThat(response.regions().get(0).rank()).isEqualTo(1);
    }

    @Test
    void getRanking_withOrderBottom_sortsByGradeAscThenScoreAsc() {
        // A등급(점수 낮은 순) → B등급 → ... → E등급
        DistrictStats a1 = statsOf("11001", "A구1", "A", "성장", new BigDecimal("0.30"));
        DistrictStats a2 = statsOf("11002", "A구2", "A", "성장", new BigDecimal("0.35"));
        DistrictStats b1 = statsOf("11003", "B구", "B", "성장", new BigDecimal("0.32"));
        DistrictStats e1 = statsOf("11004", "E구", "E", "쇠퇴", new BigDecimal("0.40"));
        when(districtStatsQueryService.latestPerDistrict()).thenReturn(List.of(e1, b1, a2, a1));
        when(districtStatsQueryService.getLatestQuarterLabel()).thenReturn("2026Q4");

        RegionRankingResponse response = service.getRanking("bottom", null);

        assertThat(response.regions()).hasSize(4);
        assertThat(response.regions().get(0).regionCode()).isEqualTo("11001"); // A, 0.30
        assertThat(response.regions().get(1).regionCode()).isEqualTo("11002"); // A, 0.35
        assertThat(response.regions().get(2).regionCode()).isEqualTo("11003"); // B
        assertThat(response.regions().get(3).regionCode()).isEqualTo("11004"); // E
        assertThat(response.regions().get(0).rank()).isEqualTo(1);
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
        DistrictStats stats = statsOf("11110", "종로구", "B", "유지", new BigDecimal("0.37"));
        when(districtStatsQueryService.forQuarter(2025, 3)).thenReturn(List.of(stats));

        RegionRankingResponse response = service.getRanking("top", "2025Q3");

        assertThat(response.quarter()).isEqualTo("2025Q3");
        assertThat(response.regions()).hasSize(1);
        assertThat(response.regions().get(0).direction()).isEqualTo("FLAT");
    }

    @Test
    void getRanking_withNullDirection_returnsFLAT() {
        DistrictStats stats = statsOf("11110", "종로구", "C", null, new BigDecimal("0.35"));
        when(districtStatsQueryService.latestPerDistrict()).thenReturn(List.of(stats));
        when(districtStatsQueryService.getLatestQuarterLabel()).thenReturn("2026Q4");

        RegionRankingResponse response = service.getRanking("top", null);

        assertThat(response.regions().get(0).direction()).isEqualTo("FLAT");
    }

    @Test
    void getRanking_limitsToFiveResults() {
        List<DistrictStats> manyStats = List.of(
                statsOf("11001", "구1", "E", "쇠퇴", new BigDecimal("0.40")),
                statsOf("11002", "구2", "E", "쇠퇴", new BigDecimal("0.39")),
                statsOf("11003", "구3", "E", "쇠퇴", new BigDecimal("0.38")),
                statsOf("11004", "구4", "E", "쇠퇴", new BigDecimal("0.37")),
                statsOf("11005", "구5", "E", "쇠퇴", new BigDecimal("0.36")),
                statsOf("11006", "구6", "E", "쇠퇴", new BigDecimal("0.35"))
        );
        when(districtStatsQueryService.latestPerDistrict()).thenReturn(manyStats);
        when(districtStatsQueryService.getLatestQuarterLabel()).thenReturn("2026Q4");

        RegionRankingResponse response = service.getRanking("top", null);

        assertThat(response.regions()).hasSize(5);
    }
}
