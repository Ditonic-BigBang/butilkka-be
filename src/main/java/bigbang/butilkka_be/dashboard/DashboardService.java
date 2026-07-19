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
import java.util.ArrayList;
import java.util.Comparator;
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

        // 실제 데이터 기반 AI briefing 생성
        String briefing = generateBriefing(latest, history);

        return new DashboardResponse(store, grade, briefing, metrics);
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

    /**
     * 변동성 큰 지표 2개를 선택해서 AI briefing 문장 생성
     */
    private String generateBriefing(DistrictStats latest, List<DistrictStats> history) {
        // 1년 전 데이터 찾기 (4분기 전)
        DistrictStats yearAgo = history.size() >= 5 ? history.get(history.size() - 5) : history.get(0);

        // 지표별 변화 계산
        List<MetricChange> changes = new ArrayList<>();

        // 유동인구
        if (latest.getFootTraffic() != null && yearAgo.getFootTraffic() != null && yearAgo.getFootTraffic() != 0) {
            double change = (latest.getFootTraffic() - yearAgo.getFootTraffic()) * 100.0 / yearAgo.getFootTraffic();
            changes.add(new MetricChange("유동인구", change, false));
        }

        // 점포수
        if (latest.getStoreCount() != null && yearAgo.getStoreCount() != null && yearAgo.getStoreCount() != 0) {
            double change = (latest.getStoreCount() - yearAgo.getStoreCount()) * 100.0 / yearAgo.getStoreCount();
            changes.add(new MetricChange("점포수", change, false));
        }

        // 폐업률 (이미 퍼센트 값으로 저장됨)
        if (latest.getClosureRate() != null && yearAgo.getClosureRate() != null) {
            double change = latest.getClosureRate().doubleValue() - yearAgo.getClosureRate().doubleValue();
            changes.add(new MetricChange("폐업률", change, true));
        }

        // 공실률 (이미 퍼센트 값으로 저장됨)
        if (latest.getVacancyRate() != null && yearAgo.getVacancyRate() != null) {
            double change = latest.getVacancyRate().doubleValue() - yearAgo.getVacancyRate().doubleValue();
            changes.add(new MetricChange("공실률", change, true));
        }

        // 매출
        if (latest.getSalesAmount() != null && yearAgo.getSalesAmount() != null && yearAgo.getSalesAmount() != 0) {
            double change = (latest.getSalesAmount() - yearAgo.getSalesAmount()) * 100.0 / yearAgo.getSalesAmount();
            changes.add(new MetricChange("매출", change, false));
        }

        // 변동성 큰 순으로 정렬 (절대값 기준)
        changes.sort(Comparator.comparingDouble((MetricChange m) -> Math.abs(m.change)).reversed());

        if (changes.size() < 2) {
            return null;
        }

        // 상위 2개 선택
        MetricChange first = changes.get(0);
        MetricChange second = changes.get(1);

        return String.format("최근 1년간 %s, %s, %s",
                formatMetricSentence(first),
                formatMetricSentence(second),
                generateConclusion(first, second));
    }

    private String formatMetricSentence(MetricChange metric) {
        String direction = metric.change >= 0 ? "증가" : "감소";
        if (metric.isRate) {
            direction = metric.change >= 0 ? "상승" : "하락";
        }
        double absChange = Math.abs(metric.change);
        return String.format("%s이(가) %.1f%% %s했고", metric.name, absChange, direction);
    }

    private String generateConclusion(MetricChange first, MetricChange second) {
        // 긍정 지표: 유동인구 증가, 점포수 증가, 매출 증가, 폐업률 감소, 공실률 감소
        int positiveCount = 0;

        if (first.name.equals("폐업률") || first.name.equals("공실률")) {
            if (first.change < 0) positiveCount++;
        } else {
            if (first.change > 0) positiveCount++;
        }

        if (second.name.equals("폐업률") || second.name.equals("공실률")) {
            if (second.change < 0) positiveCount++;
        } else {
            if (second.change > 0) positiveCount++;
        }

        if (positiveCount == 2) {
            return "상권이 성장세를 보이고 있어요.";
        } else if (positiveCount == 1) {
            return "상권이 안정적인 흐름을 유지하고 있어요.";
        } else {
            return "상권 변화에 주의가 필요해요.";
        }
    }

    private record MetricChange(String name, double change, boolean isRate) {}
}
