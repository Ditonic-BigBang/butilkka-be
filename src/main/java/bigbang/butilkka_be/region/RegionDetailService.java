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

@Service
@RequiredArgsConstructor
public class RegionDetailService {

    private static final BigDecimal SEOUL_AVG_OPERATING_YEARS = new BigDecimal("4.1");

    private final DistrictStatsQueryService districtStatsQueryService;

    public RegionDetailResponse getDetail(String code) {
        // 10자리 행정동 코드가 오면 앞 5자리로 구 코드 추출
        String districtCode = code.length() >= 5 ? code.substring(0, 5) : code;

        List<DistrictStats> history = districtStatsQueryService.historyForDistrict(districtCode);
        if (history.isEmpty()) {
            throw AppException.notFound("존재하지 않는 구코드입니다.");
        }

        DistrictStats latest = history.get(history.size() - 1);
        DistrictStats previous = history.size() >= 2 ? history.get(history.size() - 2) : null;

        return new RegionDetailResponse(
                districtCode,
                latest.getDistrictName(),  // district field
                latest.getDistrictName(),  // regionName (구 기반이므로 동일)
                label(latest),
                buildGradeSummary(history, latest, previous),
                buildMetricSummary(history, s -> s.getRentAmount(), DistrictStats::getRentDelta),
                buildMetricSummary(history, s -> s.getFootTraffic(), DistrictStats::getFootTrafficDelta),
                buildVacancyRateSummary(history),  // 공실률은 CSV에 이미 %로 저장됨
                buildClosureRateSummary(history, latest),
                buildStoreCountSummary(history, latest)
        );
    }

    private RegionDetailResponse.DeclineGradeSummary buildGradeSummary(
            List<DistrictStats> history, DistrictStats latest, DistrictStats previous) {
        List<RegionDetailResponse.GradeTrendPoint> trend = history.stream()
                .map(s -> new RegionDetailResponse.GradeTrendPoint(label(s), s.getDeclineGrade()))
                .toList();
        String previousGrade = previous != null ? previous.getDeclineGrade() : null;
        return new RegionDetailResponse.DeclineGradeSummary(latest.getDeclineGrade(), previousGrade, trend);
    }

    private MetricSummary buildMetricSummary(
            List<DistrictStats> history,
            Function<DistrictStats, Number> valueFn,
            Function<DistrictStats, Number> deltaFn) {
        DistrictStats latest = history.get(history.size() - 1);
        List<MetricTrendPoint> trend = history.stream()
                .map(s -> new MetricTrendPoint(label(s), valueFn.apply(s)))
                .toList();
        String direction = resolveDirection(deltaFn.apply(latest));
        Number deltaPercent = toPercent(deltaFn.apply(latest));
        return new MetricSummary(valueFn.apply(latest), deltaPercent, direction, trend);
    }

    private MetricSummary buildRateMetricSummary(
            List<DistrictStats> history,
            Function<DistrictStats, Number> valueFn,
            Function<DistrictStats, Number> deltaFn) {
        DistrictStats latest = history.get(history.size() - 1);
        List<MetricTrendPoint> trend = history.stream()
                .map(s -> new MetricTrendPoint(label(s), toPercent(valueFn.apply(s))))
                .toList();
        String direction = resolveDirection(deltaFn.apply(latest));
        return new MetricSummary(toPercent(valueFn.apply(latest)), toPercent(deltaFn.apply(latest)), direction, trend);
    }

    private MetricSummary buildVacancyRateSummary(List<DistrictStats> history) {
        // 공실률은 CSV에 이미 %로 저장됨 (3.32 = 3.32%)
        DistrictStats latest = history.get(history.size() - 1);
        List<MetricTrendPoint> trend = history.stream()
                .map(s -> new MetricTrendPoint(label(s), toDouble(s.getVacancyRate())))
                .toList();
        String direction = resolveDirection(latest.getVacancyRateDelta());
        return new MetricSummary(toDouble(latest.getVacancyRate()), toDouble(latest.getVacancyRateDelta()), direction, trend);
    }

    private ClosureRateSummary buildClosureRateSummary(List<DistrictStats> history, DistrictStats latest) {
        List<MetricTrendPoint> trend = history.stream()
                .map(s -> new MetricTrendPoint(label(s), toPercent(s.getClosureRate())))
                .toList();
        String direction = resolveDirection(latest.getClosureRateDelta());
        BigDecimal avgYears = latest.getAvgOperatingYears() != null ? latest.getAvgOperatingYears() : SEOUL_AVG_OPERATING_YEARS;
        return new ClosureRateSummary(
                toPercent(latest.getClosureRate()), toPercent(latest.getClosureRateDelta()), direction, trend,
                avgYears, SEOUL_AVG_OPERATING_YEARS);
    }

    private StoreCountSummary buildStoreCountSummary(List<DistrictStats> history, DistrictStats latest) {
        List<MetricTrendPoint> trend = history.stream()
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
}
