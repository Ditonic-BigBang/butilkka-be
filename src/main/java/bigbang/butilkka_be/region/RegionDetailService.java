package bigbang.butilkka_be.region;

import bigbang.butilkka_be.common.exception.AppException;
import bigbang.butilkka_be.region.dto.CategoryCount;
import bigbang.butilkka_be.region.dto.ClosureRateSummary;
import bigbang.butilkka_be.region.dto.MetricSummary;
import bigbang.butilkka_be.region.dto.MetricTrendPoint;
import bigbang.butilkka_be.region.dto.RegionDetailResponse;
import bigbang.butilkka_be.region.dto.StoreCountSummary;
import bigbang.butilkka_be.stats.DistrictStats;
import bigbang.butilkka_be.stats.DistrictStatsQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class RegionDetailService {

    private static final BigDecimal SEOUL_AVG_OPERATING_YEARS = new BigDecimal("4.1");
    private static final Pattern QUARTER_PATTERN = Pattern.compile("^(\\d{4})Q([1-4])$");
    private static final int MAX_TREND_QUARTERS = 12;

    private final DistrictStatsQueryService districtStatsQueryService;

    public RegionDetailResponse getDetail(String code, String quarterParam) {
        // 10자리 행정동 코드가 오면 앞 5자리로 구 코드 추출
        String districtCode = code.length() >= 5 ? code.substring(0, 5) : code;

        List<DistrictStats> fullHistory = districtStatsQueryService.historyForDistrict(districtCode);
        if (fullHistory.isEmpty()) {
            throw AppException.notFound("존재하지 않는 구코드입니다.");
        }

        // quarter 파라미터 파싱 및 필터링
        List<DistrictStats> history;
        if (quarterParam != null && !quarterParam.isBlank()) {
            int[] parsed = parseQuarter(quarterParam);
            int targetYear = parsed[0];
            int targetQuarter = parsed[1];

            // 해당 분기까지의 데이터만 필터링
            history = fullHistory.stream()
                    .filter(s -> s.getYear() < targetYear ||
                            (s.getYear() == targetYear && s.getQuarter() <= targetQuarter))
                    .toList();

            if (history.isEmpty()) {
                throw AppException.notFound("해당 분기(" + quarterParam + ")의 데이터가 없습니다.");
            }

            // 요청한 분기 데이터가 존재하는지 확인
            DistrictStats last = history.get(history.size() - 1);
            if (last.getYear() != targetYear || last.getQuarter() != targetQuarter) {
                throw AppException.notFound("해당 분기(" + quarterParam + ")의 데이터가 없습니다.");
            }
        } else {
            history = fullHistory;
        }

        // trend용 데이터: 최근 12분기까지만
        List<DistrictStats> trendHistory = history.size() > MAX_TREND_QUARTERS
                ? history.subList(history.size() - MAX_TREND_QUARTERS, history.size())
                : history;

        DistrictStats latest = history.get(history.size() - 1);
        DistrictStats previous = history.size() >= 2 ? history.get(history.size() - 2) : null;

        return new RegionDetailResponse(
                districtCode,
                latest.getDistrictName(),  // district field
                latest.getDistrictName(),  // regionName (구 기반이므로 동일)
                label(latest),
                buildGradeSummary(trendHistory, latest, previous),
                buildMetricSummary(trendHistory, latest, this::calcSalesPerStore, DistrictStats::getSalesDelta),  // 점포당 매출
                buildMetricSummary(trendHistory, latest, s -> s.getFootTraffic(), DistrictStats::getFootTrafficDelta),
                buildVacancyRateSummary(trendHistory, latest),  // 공실률은 CSV에 이미 %로 저장됨
                buildClosureRateSummary(trendHistory, latest),
                buildStoreCountSummary(trendHistory, latest)
        );
    }

    /**
     * "2025Q3" 형식의 문자열을 [year, quarter] 배열로 파싱
     */
    private int[] parseQuarter(String quarterStr) {
        Matcher matcher = QUARTER_PATTERN.matcher(quarterStr.trim());
        if (!matcher.matches()) {
            throw AppException.badRequest("잘못된 분기 형식입니다. 예: 2025Q3");
        }
        int year = Integer.parseInt(matcher.group(1));
        int quarter = Integer.parseInt(matcher.group(2));
        return new int[]{year, quarter};
    }

    private RegionDetailResponse.DeclineGradeSummary buildGradeSummary(
            List<DistrictStats> trendHistory, DistrictStats latest, DistrictStats previous) {
        List<RegionDetailResponse.GradeTrendPoint> trend = trendHistory.stream()
                .map(s -> new RegionDetailResponse.GradeTrendPoint(label(s), s.getDeclineGrade()))
                .toList();
        String previousGrade = previous != null ? previous.getDeclineGrade() : null;
        return new RegionDetailResponse.DeclineGradeSummary(latest.getDeclineGrade(), previousGrade, trend);
    }

    private MetricSummary buildMetricSummary(
            List<DistrictStats> trendHistory,
            DistrictStats latest,
            Function<DistrictStats, Number> valueFn,
            Function<DistrictStats, Number> deltaFn) {
        List<MetricTrendPoint> trend = trendHistory.stream()
                .map(s -> new MetricTrendPoint(label(s), valueFn.apply(s)))
                .toList();
        String direction = resolveDirection(deltaFn.apply(latest));
        Number deltaPercent = toPercent(deltaFn.apply(latest));
        return new MetricSummary(valueFn.apply(latest), deltaPercent, direction, trend);
    }

    private MetricSummary buildVacancyRateSummary(List<DistrictStats> trendHistory, DistrictStats latest) {
        // 공실률은 CSV에 이미 %로 저장됨 (3.32 = 3.32%)
        List<MetricTrendPoint> trend = trendHistory.stream()
                .map(s -> new MetricTrendPoint(label(s), toDouble(s.getVacancyRate())))
                .toList();
        String direction = resolveDirection(latest.getVacancyRateDelta());
        return new MetricSummary(toDouble(latest.getVacancyRate()), toDouble(latest.getVacancyRateDelta()), direction, trend);
    }

    private ClosureRateSummary buildClosureRateSummary(List<DistrictStats> trendHistory, DistrictStats latest) {
        List<MetricTrendPoint> trend = trendHistory.stream()
                .map(s -> new MetricTrendPoint(label(s), toPercent(s.getClosureRate())))
                .toList();
        String direction = resolveDirection(latest.getClosureRateDelta());
        BigDecimal avgYears = latest.getAvgOperatingYears() != null ? latest.getAvgOperatingYears() : SEOUL_AVG_OPERATING_YEARS;
        return new ClosureRateSummary(
                toPercent(latest.getClosureRate()), toPercent(latest.getClosureRateDelta()), direction, trend,
                avgYears, SEOUL_AVG_OPERATING_YEARS);
    }

    private StoreCountSummary buildStoreCountSummary(List<DistrictStats> trendHistory, DistrictStats latest) {
        List<MetricTrendPoint> trend = trendHistory.stream()
                .map(s -> new MetricTrendPoint(label(s), s.getStoreCount()))
                .toList();
        String direction = resolveDirection(latest.getStoreCountDelta());
        // 구 기반에서는 카테고리 분포 없이 전체 점포수만 반환
        List<CategoryCount> categoryDistribution = List.of(
                new CategoryCount("전체", latest.getStoreCount()));
        return new StoreCountSummary(latest.getStoreCount(), toPercent(latest.getStoreCountDelta()), direction, trend, categoryDistribution);
    }

    private String resolveDirection(Number delta) {
        if (delta == null) {
            return "FLAT";
        }
        double value = delta.doubleValue();
        if (value > 0) {
            return "UP";
        }
        if (value < 0) {
            return "DOWN";
        }
        return "FLAT";
    }

    private String label(DistrictStats stats) {
        return stats.getYear() + "Q" + stats.getQuarter();
    }

    private Double toPercent(Number value) {
        return value == null ? null : value.doubleValue() * 100;
    }

    private Double toDouble(Number value) {
        return value == null ? null : value.doubleValue();
    }

    /**
     * 점포당 매출 = 매출 / 점포수
     */
    private Number calcSalesPerStore(DistrictStats stats) {
        if (stats.getSalesAmount() == null || stats.getStoreCount() == null || stats.getStoreCount() == 0) {
            return null;
        }
        return stats.getSalesAmount() / stats.getStoreCount();
    }
}
