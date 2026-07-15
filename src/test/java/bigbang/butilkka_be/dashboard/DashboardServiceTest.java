package bigbang.butilkka_be.dashboard;

import bigbang.butilkka_be.category.Category;
import bigbang.butilkka_be.category.CategoryRepository;
import bigbang.butilkka_be.common.exception.AppException;
import bigbang.butilkka_be.dashboard.dto.DashboardResponse;
import bigbang.butilkka_be.stats.DistrictStats;
import bigbang.butilkka_be.stats.DistrictStatsQueryService;
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
    private DistrictStatsQueryService districtStatsQueryService;
    @Mock
    private CategoryRepository categoryRepository;

    private DashboardService service;

    @BeforeEach
    void setUp() {
        service = new DashboardService(userRepository, districtStatsQueryService, categoryRepository);
    }

    private static User userWithStore(String regionCode, String categoryCode) {
        User user = mock(User.class);
        lenient().when(user.getStoreRegion()).thenReturn(regionCode);
        lenient().when(user.getCategoryCode()).thenReturn(categoryCode);
        return user;
    }

    private static DistrictStats statsOf(
            String districtCode, String districtName, int year, int quarter, String grade,
            Long footTraffic, String footTrafficDelta, Long footTrafficGap,
            Integer storeCount, String storeCountDelta, Long storeCountGap,
            String closureRate, String closureRateDelta, Long closureRateGap) {
        DistrictStats stats = mock(DistrictStats.class);
        lenient().when(stats.getDistrictCode()).thenReturn(districtCode);
        lenient().when(stats.getDistrictName()).thenReturn(districtName);
        lenient().when(stats.getYear()).thenReturn(year);
        lenient().when(stats.getQuarter()).thenReturn(quarter);
        lenient().when(stats.getDeclineGrade()).thenReturn(grade);
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

        Category category = mock(Category.class);
        when(category.getCategoryName()).thenReturn("한식음식점");
        when(categoryRepository.findById("CS100001")).thenReturn(Optional.of(category));

        DistrictStats q1 = statsOf("11680", "강남구", 2026, 1, "B",
                128110L, "0.01", 100L, 405, "0.005", 2L, "0.034", "0.001", 0L);
        DistrictStats q2 = statsOf("11680", "강남구", 2026, 2, "B",
                125000L, "-0.015", -3110L, 401, "0.007", 3L, "0.037", "0.0", 0L);
        DistrictStats q3 = statsOf("11680", "강남구", 2026, 3, "C",
                121940L, "-0.055", -6170L, 398, "0.007", 3L, "0.039", "0.0", 0L);
        when(districtStatsQueryService.historyForDistrict("11680"))
                .thenReturn(List.of(q1, q2, q3));

        DashboardResponse response = service.getDashboard(1L);

        assertThat(response.store().regionCode()).isEqualTo("11680");
        assertThat(response.store().regionName()).isEqualTo("강남구");
        assertThat(response.store().categoryName()).isEqualTo("한식음식점");

        assertThat(response.grade().current()).isEqualTo("C");
        assertThat(response.grade().previous()).isEqualTo("B");
        assertThat(response.grade().gaugeValue()).isEqualTo(50);

        assertThat(response.metrics().footTraffic().direction()).isEqualTo("DOWN");
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
    void getDashboard_withNoStatsForDistrict_throwsNotFound() {
        User user = userWithStore("9999999999", "CS100001");
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(districtStatsQueryService.historyForDistrict("99999")).thenReturn(List.of());

        assertThatThrownBy(() -> service.getDashboard(1L))
                .isInstanceOf(AppException.class)
                .extracting(e -> ((AppException) e).getHttpStatus())
                .isEqualTo(org.springframework.http.HttpStatus.NOT_FOUND);
    }
}
