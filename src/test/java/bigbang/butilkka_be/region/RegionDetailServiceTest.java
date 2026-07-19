package bigbang.butilkka_be.region;

import bigbang.butilkka_be.common.exception.AppException;
import bigbang.butilkka_be.region.dto.RegionDetailResponse;
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
class RegionDetailServiceTest {

    @Mock
    private DistrictStatsQueryService districtStatsQueryService;

    private RegionDetailService service;

    @BeforeEach
    void setUp() {
        service = new RegionDetailService(districtStatsQueryService);
    }

    private static DistrictStats statsOf(String districtCode, String districtName, int year, int quarter, String grade,
                                          long footTraffic, BigDecimal rentAmount, BigDecimal vacancyRate,
                                          BigDecimal closureRate, int storeCount) {
        DistrictStats stats = mock(DistrictStats.class);
        when(stats.getDistrictCode()).thenReturn(districtCode);
        when(stats.getDistrictName()).thenReturn(districtName);
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
        when(stats.getStoreCount()).thenReturn(storeCount);
        when(stats.getStoreCountDelta()).thenReturn(BigDecimal.ZERO);
        when(stats.getAvgOperatingYears()).thenReturn(new BigDecimal("3.5"));
        return stats;
    }

    @Test
    void getDetail_withHistory_buildsAllMetricSummaries() {
        DistrictStats q1 = statsOf("11680", "강남구", 2026, 1, "B", 100000L, new BigDecimal("40000000"), new BigDecimal("0.04"), new BigDecimal("0.05"), 450);
        DistrictStats q2 = statsOf("11680", "강남구", 2026, 2, "A", 110000L, new BigDecimal("41000000"), new BigDecimal("0.035"), new BigDecimal("0.045"), 452);
        when(districtStatsQueryService.historyForDistrict("11680")).thenReturn(List.of(q1, q2));

        RegionDetailResponse response = service.getDetail("1168064000", null); // 10자리 코드 → 앞 5자리로 구코드 추출

        assertThat(response.regionCode()).isEqualTo("11680");
        assertThat(response.regionName()).isEqualTo("강남구");
        assertThat(response.declineGrade().current()).isEqualTo("A");
        assertThat(response.declineGrade().previous()).isEqualTo("B");
        assertThat(response.declineGrade().trend()).hasSize(2);
        assertThat(response.footTraffic().value()).isEqualTo(110000L);
        assertThat(response.storeCount().categoryDistribution()).hasSize(1);
        assertThat(response.storeCount().categoryDistribution().get(0).category()).isEqualTo("전체");
    }

    @Test
    void getDetail_with5DigitCode_worksDirectly() {
        DistrictStats stats = statsOf("11110", "종로구", 2026, 1, "C", 80000L, new BigDecimal("30000000"), new BigDecimal("0.05"), new BigDecimal("0.06"), 300);
        when(districtStatsQueryService.historyForDistrict("11110")).thenReturn(List.of(stats));

        RegionDetailResponse response = service.getDetail("11110", null);

        assertThat(response.regionCode()).isEqualTo("11110");
        assertThat(response.regionName()).isEqualTo("종로구");
    }

    @Test
    void getDetail_withUnknownDistrict_throwsNotFound() {
        when(districtStatsQueryService.historyForDistrict("99999")).thenReturn(List.of());

        assertThatThrownBy(() -> service.getDetail("99999", null))
                .isInstanceOf(AppException.class)
                .extracting(e -> ((AppException) e).getHttpStatus())
                .isEqualTo(org.springframework.http.HttpStatus.NOT_FOUND);
    }

    @Test
    void getDetail_withQuarterParam_returnsSpecificQuarter() {
        DistrictStats q1 = statsOf("11680", "강남구", 2025, 1, "C", 90000L, new BigDecimal("38000000"), new BigDecimal("0.05"), new BigDecimal("0.06"), 440);
        DistrictStats q2 = statsOf("11680", "강남구", 2025, 2, "B", 95000L, new BigDecimal("39000000"), new BigDecimal("0.045"), new BigDecimal("0.055"), 445);
        DistrictStats q3 = statsOf("11680", "강남구", 2025, 3, "B", 100000L, new BigDecimal("40000000"), new BigDecimal("0.04"), new BigDecimal("0.05"), 450);
        DistrictStats q4 = statsOf("11680", "강남구", 2025, 4, "A", 110000L, new BigDecimal("41000000"), new BigDecimal("0.035"), new BigDecimal("0.045"), 455);
        when(districtStatsQueryService.historyForDistrict("11680")).thenReturn(List.of(q1, q2, q3, q4));

        // 2025Q3 요청 시 Q3 기준 데이터 반환
        RegionDetailResponse response = service.getDetail("11680", "2025Q3");

        assertThat(response.quarter()).isEqualTo("2025Q3");
        assertThat(response.declineGrade().current()).isEqualTo("B");  // Q3 등급
        assertThat(response.declineGrade().previous()).isEqualTo("B"); // Q2 등급
        assertThat(response.footTraffic().value()).isEqualTo(100000L); // Q3 유동인구
        assertThat(response.declineGrade().trend()).hasSize(3); // Q1, Q2, Q3
    }

    @Test
    void getDetail_withInvalidQuarterFormat_throwsBadRequest() {
        // 잘못된 형식이어도 먼저 DB 조회가 일어나므로 stub 필요
        DistrictStats q1 = statsOf("11680", "강남구", 2025, 1, "C", 90000L, new BigDecimal("38000000"), new BigDecimal("0.05"), new BigDecimal("0.06"), 440);
        when(districtStatsQueryService.historyForDistrict("11680")).thenReturn(List.of(q1));

        assertThatThrownBy(() -> service.getDetail("11680", "2025-Q3"))
                .isInstanceOf(AppException.class)
                .extracting(e -> ((AppException) e).getHttpStatus())
                .isEqualTo(org.springframework.http.HttpStatus.BAD_REQUEST);
    }

    @Test
    void getDetail_withNonexistentQuarter_throwsNotFound() {
        DistrictStats q1 = statsOf("11680", "강남구", 2025, 1, "C", 90000L, new BigDecimal("38000000"), new BigDecimal("0.05"), new BigDecimal("0.06"), 440);
        when(districtStatsQueryService.historyForDistrict("11680")).thenReturn(List.of(q1));

        // 2025Q3 요청했지만 Q1 데이터만 존재
        assertThatThrownBy(() -> service.getDetail("11680", "2025Q3"))
                .isInstanceOf(AppException.class)
                .extracting(e -> ((AppException) e).getHttpStatus())
                .isEqualTo(org.springframework.http.HttpStatus.NOT_FOUND);
    }
}
