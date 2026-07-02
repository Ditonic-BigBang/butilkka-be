package bigbang.butilkka_be.region;

import bigbang.butilkka_be.category.Category;
import bigbang.butilkka_be.category.CategoryRepository;
import bigbang.butilkka_be.common.exception.AppException;
import bigbang.butilkka_be.region.dto.RegionDetailResponse;
import bigbang.butilkka_be.stats.CommercialStats;
import bigbang.butilkka_be.stats.CommercialStatsQueryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class RegionDetailServiceTest {

    @Mock
    private CommercialStatsQueryService commercialStatsQueryService;
    @Mock
    private RegionRepository regionRepository;
    @Mock
    private DistrictRepository districtRepository;
    @Mock
    private CategoryRepository categoryRepository;

    private RegionDetailService service;

    @BeforeEach
    void setUp() {
        service = new RegionDetailService(commercialStatsQueryService, regionRepository, districtRepository, categoryRepository);
    }

    private static CommercialStats statsOf(int year, int quarter, String grade, int footTraffic, long rentAmount,
                                            BigDecimal vacancyRate, BigDecimal closureRate, BigDecimal avgBusinessPeriod,
                                            int storeCount, String categoryCode) {
        CommercialStats stats = mock(CommercialStats.class);
        when(stats.getYear()).thenReturn(year);
        when(stats.getQuarter()).thenReturn(quarter);
        when(stats.getDeclineGrade()).thenReturn(grade);
        when(stats.getFootTraffic()).thenReturn(footTraffic);
        when(stats.getFootTrafficDelta()).thenReturn(BigDecimal.ZERO);
        when(stats.getRentAmount()).thenReturn(rentAmount);
        when(stats.getRentDelta()).thenReturn(BigDecimal.ZERO);
        when(stats.getVacancyRate()).thenReturn(vacancyRate);
        when(stats.getVacancyRateDelta()).thenReturn(BigDecimal.ZERO);
        when(stats.getClosureRate()).thenReturn(closureRate);
        when(stats.getClosureRateDelta()).thenReturn(BigDecimal.ZERO);
        when(stats.getAvgBusinessPeriod()).thenReturn(avgBusinessPeriod);
        when(stats.getStoreCount()).thenReturn(storeCount);
        when(stats.getStoreCountDelta()).thenReturn(BigDecimal.ZERO);
        when(stats.getCategoryCode()).thenReturn(categoryCode);
        when(stats.getRegionCode()).thenReturn("1168064000");
        return stats;
    }

    @Test
    void getDetail_withHistory_buildsAllMetricSummaries() {
        CommercialStats q1 = statsOf(2026, 1, "B", 100000, 40000000L, new BigDecimal("4.0"), new BigDecimal("5.0"), new BigDecimal("3.0"), 450, "CS100001");
        CommercialStats q2 = statsOf(2026, 2, "A", 110000, 41000000L, new BigDecimal("3.5"), new BigDecimal("4.5"), new BigDecimal("3.1"), 452, "CS100001");
        when(commercialStatsQueryService.historyForRegion("1168064000")).thenReturn(List.of(q1, q2));

        Region region = mock(Region.class);
        when(region.getRegionCode()).thenReturn("1168064000");
        when(region.getRegionName()).thenReturn("역삼1동");
        when(region.getDistrictCode()).thenReturn("11680");
        when(regionRepository.findById("1168064000")).thenReturn(Optional.of(region));

        District district = mock(District.class);
        when(district.getDistrictName()).thenReturn("강남구");
        when(districtRepository.findById("11680")).thenReturn(Optional.of(district));

        Category category = mock(Category.class);
        when(category.getCategoryName()).thenReturn("한식음식점");
        when(categoryRepository.findById("CS100001")).thenReturn(Optional.of(category));

        RegionDetailResponse response = service.getDetail("1168064000");

        assertThat(response.regionName()).isEqualTo("역삼1동");
        assertThat(response.declineGrade().current()).isEqualTo("A");
        assertThat(response.declineGrade().previous()).isEqualTo("B");
        assertThat(response.declineGrade().trend()).hasSize(2);
        assertThat(response.footTraffic().value()).isEqualTo(110000);
        assertThat(response.storeCount().categoryDistribution()).hasSize(1);
        assertThat(response.storeCount().categoryDistribution().get(0).category()).isEqualTo("한식음식점");
    }

    @Test
    void getDetail_withUnknownRegion_throwsNotFound() {
        when(regionRepository.findById("UNKNOWN")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getDetail("UNKNOWN"))
                .isInstanceOf(AppException.class)
                .extracting(e -> ((AppException) e).getHttpStatus())
                .isEqualTo(org.springframework.http.HttpStatus.NOT_FOUND);
    }
}
