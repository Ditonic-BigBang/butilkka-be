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
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;
import java.util.function.Function;

@Service
@RequiredArgsConstructor
public class DashboardService {

    private final UserRepository userRepository;
    private final CommercialStatsQueryService commercialStatsQueryService;
    private final RegionRepository regionRepository;
    private final DistrictRepository districtRepository;
    private final CategoryRepository categoryRepository;

    public DashboardResponse getDashboard(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> AppException.notFound("사용자를 찾을 수 없습니다"));

        if (user.getStoreRegion() == null) {
            throw AppException.notFound("등록된 가게 정보가 없습니다.");
        }

        List<CommercialStats> history = commercialStatsQueryService.historyForRegion(user.getStoreRegion());
        if (history.isEmpty()) {
            throw AppException.notFound("등록된 가게 정보가 없습니다.");
        }

        CommercialStats latest = history.get(history.size() - 1);
        String previousGrade = history.size() >= 2
                ? history.get(history.size() - 2).getDeclineGrade()
                : null;

        DashboardResponse.StoreInfo store = buildStore(user);
        DashboardResponse.Grade grade = new DashboardResponse.Grade(
                latest.getDeclineGrade(), previousGrade, gaugeValueOf(latest.getDeclineGrade()));

        DashboardResponse.Metrics metrics = new DashboardResponse.Metrics(
                trendOf(history, latest.getFootTrafficDelta(), latest.getFootTrafficGap(), CommercialStats::getFootTraffic),
                trendOf(history, latest.getStoreCountDelta(), latest.getStoreCountGap(), CommercialStats::getStoreCount),
                trendOf(history, latest.getClosureRateDelta(), latest.getClosureRateGap(), CommercialStats::getClosureRate));

        return new DashboardResponse(store, grade, latest.getBriefing(), metrics);
    }

    private DashboardResponse.StoreInfo buildStore(User user) {
        Region region = regionRepository.findById(user.getStoreRegion())
                .orElseThrow(() -> AppException.notFound("존재하지 않는 상권코드입니다."));
        District district = districtRepository.findById(region.getDistrictCode())
                .orElseThrow(() -> AppException.notFound("존재하지 않는 자치구코드입니다."));
        Category category = categoryRepository.findById(user.getCategoryCode())
                .orElseThrow(() -> AppException.notFound("존재하지 않는 업종코드입니다."));
        return new DashboardResponse.StoreInfo(
                region.getRegionCode(), region.getRegionName(), category.getCategoryName(), district.getDistrictName());
    }

    private int gaugeValueOf(String grade) {
        if (grade == null) {
            return 50; // 기본값: C등급 수준
        }
        return switch (grade) {
            case "A" -> 90;
            case "B" -> 70;
            case "C" -> 50;
            case "D" -> 30;
            case "E" -> 10;
            default -> 50;
        };
    }

    private DashboardResponse.MetricTrend trendOf(
            List<CommercialStats> history, BigDecimal delta, Long gap,
            Function<CommercialStats, Number> valueExtractor) {
        String direction = (delta != null && delta.signum() < 0) ? "DOWN" : "UP";
        Double deltaAbs = delta == null ? null : Math.abs(delta.doubleValue());
        Long gapAbs = gap == null ? null : Math.abs(gap);

        List<DashboardResponse.Point> points = history.stream()
                .skip(Math.max(0, history.size() - 3))
                .map(stats -> new DashboardResponse.Point(
                        stats.getYear() + "Q" + stats.getQuarter(), toDouble(valueExtractor.apply(stats))))
                .toList();

        return new DashboardResponse.MetricTrend(direction, deltaAbs, gapAbs, points);
    }

    private Double toDouble(Number number) {
        return number == null ? null : number.doubleValue();
    }
}
