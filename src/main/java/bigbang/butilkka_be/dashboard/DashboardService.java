package bigbang.butilkka_be.dashboard;

import bigbang.butilkka_be.category.Category;
import bigbang.butilkka_be.category.CategoryRepository;
import bigbang.butilkka_be.common.exception.AppException;
import bigbang.butilkka_be.dashboard.dto.DashboardResponse;
import bigbang.butilkka_be.stats.DistrictStats;
import bigbang.butilkka_be.stats.DistrictStatsQueryService;
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
    private final DistrictStatsQueryService districtStatsQueryService;
    private final CategoryRepository categoryRepository;

    public DashboardResponse getDashboard(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> AppException.notFound("사용자를 찾을 수 없습니다"));

        if (user.getStoreRegion() == null) {
            throw AppException.notFound("등록된 가게 정보가 없습니다.");
        }

        // 10자리 행정동 코드에서 앞 5자리 구코드 추출
        String districtCode = user.getStoreRegion().substring(0, 5);

        List<DistrictStats> history = districtStatsQueryService.historyForDistrict(districtCode);
        if (history.isEmpty()) {
            throw AppException.notFound("등록된 가게 정보가 없습니다.");
        }

        DistrictStats latest = history.get(history.size() - 1);
        String previousGrade = history.size() >= 2
                ? history.get(history.size() - 2).getDeclineGrade()
                : null;

        DashboardResponse.StoreInfo store = buildStore(user, latest);
        DashboardResponse.Grade grade = new DashboardResponse.Grade(
                latest.getDeclineGrade(), previousGrade, gaugeValueOf(latest.getDeclineGrade()));

        DashboardResponse.Metrics metrics = new DashboardResponse.Metrics(
                trendOf(history, latest.getFootTrafficDelta(), latest.getFootTrafficGap(), DistrictStats::getFootTraffic, "만명"),
                trendOf(history, latest.getStoreCountDelta(), latest.getStoreCountGap(), DistrictStats::getStoreCount, "개"),
                rateTrendOf(history, latest.getClosureRateDelta(), latest.getClosureRateGap(), DistrictStats::getClosureRate, "%p"));

        // briefing이 DistrictStats에 없으므로 null 처리
        return new DashboardResponse(store, grade, null, metrics);
    }

    private DashboardResponse.StoreInfo buildStore(User user, DistrictStats stats) {
        Category category = categoryRepository.findById(user.getCategoryCode())
                .orElseThrow(() -> AppException.notFound("존재하지 않는 업종코드입니다."));
        // 구 기반: districtCode, districtName 사용
        return new DashboardResponse.StoreInfo(
                stats.getDistrictCode(), stats.getDistrictName(), category.getCategoryName(), stats.getDistrictName());
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
            List<DistrictStats> history, BigDecimal delta, Long gap,
            Function<DistrictStats, Number> valueExtractor, String unit) {
        String direction = (delta != null && delta.signum() < 0) ? "DOWN" : "UP";
        Double deltaAbs = delta == null ? null : Math.round(Math.abs(delta.doubleValue() * 100) * 100) / 100.0;
        Long gapAbs = gap == null ? null : Math.abs(gap);
        String gapText = formatGapText(gapAbs, unit);

        List<DashboardResponse.Point> points = history.stream()
                .skip(Math.max(0, history.size() - 3))
                .map(stats -> new DashboardResponse.Point(
                        stats.getYear() + "Q" + stats.getQuarter(), toDouble(valueExtractor.apply(stats))))
                .toList();

        return new DashboardResponse.MetricTrend(direction, deltaAbs, gapAbs, gapText, points);
    }

    private String formatGapText(Long gap, String unit) {
        if (gap == null) {
            return null;
        }
        if ("만명".equals(unit)) {
            double inMan = gap / 10000.0;
            return String.format("%.1f %s", inMan, unit).replace(".", ",");
        }
        return gap + " " + unit;
    }

    private Double toDouble(Number number) {
        return number == null ? null : number.doubleValue();
    }

    private DashboardResponse.MetricTrend rateTrendOf(
            List<DistrictStats> history, BigDecimal delta, Long gap,
            Function<DistrictStats, Number> valueExtractor, String unit) {
        String direction = (delta != null && delta.signum() < 0) ? "DOWN" : "UP";
        Double deltaAbs = delta == null ? null : Math.round(Math.abs(delta.doubleValue() * 100) * 100) / 100.0;
        Long gapAbs = gap == null ? null : Math.abs(gap);
        String gapText = formatGapText(gapAbs, unit);

        List<DashboardResponse.Point> points = history.stream()
                .skip(Math.max(0, history.size() - 3))
                .map(stats -> new DashboardResponse.Point(
                        stats.getYear() + "Q" + stats.getQuarter(), toPercent(valueExtractor.apply(stats))))
                .toList();

        return new DashboardResponse.MetricTrend(direction, deltaAbs, gapAbs, gapText, points);
    }

    private Double toPercent(Number number) {
        return number == null ? null : number.doubleValue() * 100;
    }
}
