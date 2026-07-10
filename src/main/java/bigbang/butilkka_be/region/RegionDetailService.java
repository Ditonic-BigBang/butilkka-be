package bigbang.butilkka_be.region;

import bigbang.butilkka_be.category.Category;
import bigbang.butilkka_be.category.CategoryRepository;
import bigbang.butilkka_be.common.exception.AppException;
import bigbang.butilkka_be.region.dto.CategoryCount;
import bigbang.butilkka_be.region.dto.ClosureRateSummary;
import bigbang.butilkka_be.region.dto.MetricSummary;
import bigbang.butilkka_be.region.dto.MetricTrendPoint;
import bigbang.butilkka_be.region.dto.RegionDetailResponse;
import bigbang.butilkka_be.region.dto.StoreCountSummary;
import bigbang.butilkka_be.stats.CommercialStats;
import bigbang.butilkka_be.stats.CommercialStatsQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;
import java.util.function.Function;

@Service
@RequiredArgsConstructor
public class RegionDetailService {

    private static final BigDecimal SEOUL_AVG_OPERATING_YEARS = new BigDecimal("4.1");

    private final CommercialStatsQueryService commercialStatsQueryService;
    private final RegionRepository regionRepository;
    private final DistrictRepository districtRepository;
    private final CategoryRepository categoryRepository;

    public RegionDetailResponse getDetail(String regionCode) {
        Region region = regionRepository.findById(regionCode)
                .orElseThrow(() -> AppException.notFound("존재하지 않는 상권코드입니다."));
        District district = districtRepository.findById(region.getDistrictCode())
                .orElseThrow(() -> AppException.notFound("존재하지 않는 자치구코드입니다."));

        List<CommercialStats> history = commercialStatsQueryService.historyForRegion(regionCode);
        if (history.isEmpty()) {
            throw AppException.notFound("존재하지 않는 상권코드입니다.");
        }

        CommercialStats latest = history.get(history.size() - 1);
        CommercialStats previous = history.size() >= 2 ? history.get(history.size() - 2) : null;

        return new RegionDetailResponse(
                region.getRegionCode(),
                district.getDistrictName(),
                region.getRegionName(),
                label(latest),
                buildGradeSummary(history, latest, previous),
                buildMetricSummary(history, CommercialStats::getRentAmount, CommercialStats::getRentDelta),
                buildMetricSummary(history, s -> s.getFootTraffic(), CommercialStats::getFootTrafficDelta),
                buildRateMetricSummary(history, CommercialStats::getVacancyRate, CommercialStats::getVacancyRateDelta),
                buildClosureRateSummary(history, latest),
                buildStoreCountSummary(history, latest)
        );
    }

    private RegionDetailResponse.DeclineGradeSummary buildGradeSummary(
            List<CommercialStats> history, CommercialStats latest, CommercialStats previous) {
        List<RegionDetailResponse.GradeTrendPoint> trend = history.stream()
                .map(s -> new RegionDetailResponse.GradeTrendPoint(label(s), s.getDeclineGrade()))
                .toList();
        String previousGrade = previous != null ? previous.getDeclineGrade() : null;
        return new RegionDetailResponse.DeclineGradeSummary(latest.getDeclineGrade(), previousGrade, trend);
    }

    private MetricSummary buildMetricSummary(
            List<CommercialStats> history,
            Function<CommercialStats, Number> valueFn,
            Function<CommercialStats, Number> deltaFn) {
        CommercialStats latest = history.get(history.size() - 1);
        List<MetricTrendPoint> trend = history.stream()
                .map(s -> new MetricTrendPoint(label(s), valueFn.apply(s)))
                .toList();
        String direction = resolveDirection(deltaFn.apply(latest));
        Number deltaPercent = toPercent(deltaFn.apply(latest));
        return new MetricSummary(valueFn.apply(latest), deltaPercent, direction, trend);
    }

    private MetricSummary buildRateMetricSummary(
            List<CommercialStats> history,
            Function<CommercialStats, Number> valueFn,
            Function<CommercialStats, Number> deltaFn) {
        CommercialStats latest = history.get(history.size() - 1);
        List<MetricTrendPoint> trend = history.stream()
                .map(s -> new MetricTrendPoint(label(s), toPercent(valueFn.apply(s))))
                .toList();
        String direction = resolveDirection(deltaFn.apply(latest));
        return new MetricSummary(toPercent(valueFn.apply(latest)), toPercent(deltaFn.apply(latest)), direction, trend);
    }

    private ClosureRateSummary buildClosureRateSummary(List<CommercialStats> history, CommercialStats latest) {
        List<MetricTrendPoint> trend = history.stream()
                .map(s -> new MetricTrendPoint(label(s), toPercent(s.getClosureRate())))
                .toList();
        String direction = resolveDirection(latest.getClosureRateDelta());
        return new ClosureRateSummary(
                toPercent(latest.getClosureRate()), toPercent(latest.getClosureRateDelta()), direction, trend,
                latest.getAvgBusinessPeriod(), SEOUL_AVG_OPERATING_YEARS);
    }

    private StoreCountSummary buildStoreCountSummary(List<CommercialStats> history, CommercialStats latest) {
        List<MetricTrendPoint> trend = history.stream()
                .map(s -> new MetricTrendPoint(label(s), s.getStoreCount()))
                .toList();
        String direction = resolveDirection(latest.getStoreCountDelta());
        Category category = categoryRepository.findById(latest.getCategoryCode())
                .orElseThrow(() -> AppException.notFound("존재하지 않는 업종코드입니다."));
        List<CategoryCount> categoryDistribution = List.of(
                new CategoryCount(category.getCategoryName(), latest.getStoreCount()));
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

    private String label(CommercialStats stats) {
        return stats.getYear() + "Q" + stats.getQuarter();
    }

    private Double toPercent(Number value) {
        return value == null ? null : value.doubleValue() * 100;
    }
}
