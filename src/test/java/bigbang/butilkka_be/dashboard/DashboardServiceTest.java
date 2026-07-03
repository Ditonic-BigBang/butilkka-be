package bigbang.butilkka_be.dashboard;

import bigbang.butilkka_be.category.Category;
import bigbang.butilkka_be.category.CategoryRepository;
import bigbang.butilkka_be.common.exception.AppException;
import bigbang.butilkka_be.dashboard.dto.DashboardResponse;
import bigbang.butilkka_be.region.District;
import bigbang.butilkka_be.region.DistrictRepository;
import bigbang.butilkka_be.region.Region;
import bigbang.butilkka_be.region.RegionRepository;
import bigbang.butilkka_be.stats.CommercialStats;
import bigbang.butilkka_be.stats.CommercialStatsQueryService;
import bigbang.butilkka_be.user.User;
import bigbang.butilkka_be.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DashboardServiceTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private CommercialStatsQueryService commercialStatsQueryService;
    @Mock
    private RegionRepository regionRepository;
    @Mock
    private DistrictRepository districtRepository;
    @Mock
    private CategoryRepository categoryRepository;

    private DashboardService service;

    @BeforeEach
    void setUp() {
        service = new DashboardService(
                userRepository, commercialStatsQueryService, regionRepository, districtRepository, categoryRepository);
    }

    private static User userWithStore(String regionCode, String categoryCode) {
        User user = mock(User.class);
        lenient().when(user.getStoreRegion()).thenReturn(regionCode);
        lenient().when(user.getCategoryCode()).thenReturn(categoryCode);
        return user;
    }

    private void stubRegionDistrictCategory() {
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
    }

    private static CommercialStats statsOf(
            int year, int quarter, String grade, String briefing,
            Integer footTraffic, String footTrafficDelta, Long footTrafficGap,
            Integer storeCount, String storeCountDelta, Long storeCountGap,
            String closureRate, String closureRateDelta, Long closureRateGap) {
        CommercialStats stats = mock(CommercialStats.class);
        lenient().when(stats.getYear()).thenReturn(year);
        lenient().when(stats.getQuarter()).thenReturn(quarter);
        lenient().when(stats.getDeclineGrade()).thenReturn(grade);
        lenient().when(stats.getBriefing()).thenReturn(briefing);
        lenient().when(stats.getFootTraffic()).thenReturn(footTraffic);
        lenient().when(stats.getFootTrafficDelta()).thenReturn(toBigDecimal(footTrafficDelta));
        lenient().when(stats.getFootTrafficGap()).thenReturn(footTrafficGap);
        lenient().when(stats.getStoreCount()).thenReturn(storeCount);
        lenient().when(stats.getStoreCountDelta()).thenReturn(toBigDecimal(storeCountDelta));
        lenient().when(stats.getStoreCountGap()).thenReturn(storeCountGap);
        lenient().when(stats.getClosureRate()).thenReturn(toBigDecimal(closureRate));
        lenient().when(stats.getClosureRateDelta()).thenReturn(toBigDecimal(closureRateDelta));
        lenient().when(stats.getClosureRateGap()).thenReturn(closureRateGap);
        return stats;
    }

    private static BigDecimal toBigDecimal(String value) {
        return value == null ? null : new BigDecimal(value);
    }

    @Test
    void getDashboard_returnsFullDashboard() {
        User user = userWithStore("1168064000", "CS100001");
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        stubRegionDistrictCategory();

        CommercialStats q4_2025 = statsOf(2025, 4, "B", "이전 브리핑",
                132423, "0.0", 0L, 412, "0.0", 0L, "3.1", "0.0", 0L);
        CommercialStats q1_2026 = statsOf(2026, 1, "B", "이전 브리핑2",
                128110, "1.0", 100L, 405, "0.5", 2L, "3.4", "0.1", 0L);
        CommercialStats q2_2026 = statsOf(2026, 2, "B", "이전 브리핑3",
                125000, "-1.5", -3110L, 401, "0.7", 3L, "3.7", "0.0", 0L);
        CommercialStats q3_2026 = statsOf(2026, 3, "C", "유동인구 감소와 공실 증가가 겹치는 주의 구간입니다.",
                121940, "-5.5", -6170L, 398, "0.7", 3L, "3.9", "0.0", 0L);
        when(commercialStatsQueryService.historyForRegion("1168064000"))
                .thenReturn(List.of(q4_2025, q1_2026, q2_2026, q3_2026));

        DashboardResponse response = service.getDashboard(1L);

        assertThat(response.store().regionCode()).isEqualTo("1168064000");
        assertThat(response.store().regionName()).isEqualTo("역삼1동");
        assertThat(response.store().district()).isEqualTo("강남구");
        assertThat(response.store().categoryName()).isEqualTo("한식음식점");

        assertThat(response.grade().current()).isEqualTo("C");
        assertThat(response.grade().previous()).isEqualTo("B");
        assertThat(response.grade().gaugeValue()).isEqualTo(50);

        assertThat(response.briefing()).isEqualTo("유동인구 감소와 공실 증가가 겹치는 주의 구간입니다.");

        assertThat(response.metrics().footTraffic().direction()).isEqualTo("DOWN");
        assertThat(response.metrics().footTraffic().delta()).isEqualTo(5.5);
        assertThat(response.metrics().footTraffic().gap()).isEqualTo(6170L);
        assertThat(response.metrics().footTraffic().points()).hasSize(3);
        assertThat(response.metrics().footTraffic().points().get(0).quarter()).isEqualTo("2026Q1");
        assertThat(response.metrics().footTraffic().points().get(0).value()).isEqualTo(128110.0);
        assertThat(response.metrics().footTraffic().points().get(2).quarter()).isEqualTo("2026Q3");
        assertThat(response.metrics().footTraffic().points().get(2).value()).isEqualTo(121940.0);

        assertThat(response.metrics().storeCount().direction()).isEqualTo("UP");
        assertThat(response.metrics().storeCount().delta()).isEqualTo(0.7);
        assertThat(response.metrics().storeCount().gap()).isEqualTo(3L);

        assertThat(response.metrics().closureRate().direction()).isEqualTo("UP");
        assertThat(response.metrics().closureRate().delta()).isEqualTo(0.0);
        assertThat(response.metrics().closureRate().gap()).isEqualTo(0L);
        assertThat(response.metrics().closureRate().points().get(2).value()).isEqualTo(3.9);
    }

    @Test
    void getDashboard_withNoStore_throwsNotFound() {
        User user = userWithStore(null, null);
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> service.getDashboard(1L))
                .isInstanceOf(AppException.class)
                .extracting(e -> ((AppException) e).getHttpStatus())
                .isEqualTo(org.springframework.http.HttpStatus.NOT_FOUND);
    }

    @Test
    void getDashboard_withNoStatsForRegion_throwsNotFound() {
        User user = userWithStore("9999999999", "CS100001");
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(commercialStatsQueryService.historyForRegion("9999999999")).thenReturn(List.of());

        assertThatThrownBy(() -> service.getDashboard(1L))
                .isInstanceOf(AppException.class)
                .extracting(e -> ((AppException) e).getHttpStatus())
                .isEqualTo(org.springframework.http.HttpStatus.NOT_FOUND);
    }

    @Test
    void getDashboard_withOnlyOneQuarter_previousIsNullAndSinglePoint() {
        User user = userWithStore("1168064000", "CS100001");
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        stubRegionDistrictCategory();

        CommercialStats onlyQuarter = statsOf(2026, 1, "A", "첫 분기 브리핑",
                50000, "0.0", 0L, 100, "0.0", 0L, "2.0", "0.0", 0L);
        when(commercialStatsQueryService.historyForRegion("1168064000")).thenReturn(List.of(onlyQuarter));

        DashboardResponse response = service.getDashboard(1L);

        assertThat(response.grade().current()).isEqualTo("A");
        assertThat(response.grade().previous()).isNull();
        assertThat(response.grade().gaugeValue()).isEqualTo(90);
        assertThat(response.metrics().footTraffic().points()).hasSize(1);
        assertThat(response.metrics().footTraffic().points().get(0).quarter()).isEqualTo("2026Q1");
    }

    @Test
    void getDashboard_withNullDeltaGapAndValue_returnsNullsAndDefaultsToUp() {
        User user = userWithStore("1168064000", "CS100001");
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        stubRegionDistrictCategory();

        CommercialStats statsWithNulls = statsOf(2026, 1, "B", "브리핑",
                null, null, null, 100, "0.0", 0L, "2.0", "0.0", 0L);
        when(commercialStatsQueryService.historyForRegion("1168064000")).thenReturn(List.of(statsWithNulls));

        DashboardResponse response = service.getDashboard(1L);

        assertThat(response.metrics().footTraffic().direction()).isEqualTo("UP");
        assertThat(response.metrics().footTraffic().delta()).isNull();
        assertThat(response.metrics().footTraffic().gap()).isNull();
        assertThat(response.metrics().footTraffic().points().get(0).value()).isNull();
    }
}
