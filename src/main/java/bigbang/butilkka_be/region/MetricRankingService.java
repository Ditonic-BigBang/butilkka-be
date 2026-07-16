package bigbang.butilkka_be.region;

import bigbang.butilkka_be.common.exception.AppException;
import bigbang.butilkka_be.region.dto.MetricRankingItem;
import bigbang.butilkka_be.region.dto.MetricRankingResponse;
import bigbang.butilkka_be.stats.DistrictStats;
import bigbang.butilkka_be.stats.DistrictStatsQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class MetricRankingService {

    private static final Set<String> SUPPORTED_METRICS =
            Set.of("rentRatio", "footTraffic", "vacancyRate", "closureRate", "storeCount");
    private static final Pattern QUARTER_PATTERN = Pattern.compile("(\\d{4})Q([1-4])");

    private final DistrictStatsQueryService districtStatsQueryService;

    public MetricRankingResponse getMetricRanking(String metric, String order, String quarterParam) {
        validateMetric(metric);
        validateOrder(order);

        List<DistrictStats> statsList;
        String quarterLabel;

        if (quarterParam == null || quarterParam.isBlank()) {
            statsList = districtStatsQueryService.latestPerDistrict();
            quarterLabel = districtStatsQueryService.getLatestQuarterLabel();
        } else {
            int[] parsed = parseQuarterLabel(quarterParam)
                    .orElseThrow(() -> AppException.badRequest("지원하지 않는 분기 형식입니다."));
            statsList = districtStatsQueryService.forQuarter(parsed[0], parsed[1]);
            quarterLabel = quarterParam;
        }

        Comparator<DistrictStats> comparator = getComparator(metric, order);
        List<DistrictStats> sorted = statsList.stream()
                .filter(s -> extractMetricValue(s, metric) != null)
                .sorted(comparator)
                .limit(5)
                .toList();

        List<MetricRankingItem> items = new ArrayList<>();
        for (int i = 0; i < sorted.size(); i++) {
            items.add(toRankingItem(sorted.get(i), metric, i + 1));
        }

        // closureRate일 때만 avgOperatingYears 포함 (서울 평균 등)
        BigDecimal avgOperatingYears = "closureRate".equals(metric)
                ? calculateSeoulAvgOperatingYears(statsList)
                : null;

        return new MetricRankingResponse(metric, order, quarterLabel, items, avgOperatingYears);
    }

    private MetricRankingItem toRankingItem(DistrictStats stats, String metric, int rank) {
        BigDecimal value = extractMetricValue(stats, metric);
        String direction = stats.getDirection() != null ? stats.getDirection() : "FLAT";
        // direction 값을 FE 기대 형식으로 변환 (성장→UP, 쇠퇴→DOWN, 유지/정체→FLAT)
        direction = switch (direction) {
            case "성장" -> "UP";
            case "쇠퇴" -> "DOWN";
            default -> "FLAT";
        };
        return new MetricRankingItem(
                rank,
                stats.getDistrictCode(),
                stats.getDistrictName(),
                value,
                direction
        );
    }

    private BigDecimal calculateSeoulAvgOperatingYears(List<DistrictStats> statsList) {
        return statsList.stream()
                .map(DistrictStats::getAvgOperatingYears)
                .filter(v -> v != null)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .divide(BigDecimal.valueOf(Math.max(1, statsList.size())), 2, java.math.RoundingMode.HALF_UP);
    }

    private Comparator<DistrictStats> getComparator(String metric, String order) {
        Comparator<DistrictStats> byValue = Comparator.comparing(
                s -> extractMetricValue(s, metric),
                Comparator.nullsLast(Comparator.naturalOrder())
        );
        return "top".equals(order) ? byValue.reversed() : byValue;
    }

    private BigDecimal extractMetricValue(DistrictStats stats, String metric) {
        return switch (metric) {
            case "rentRatio" -> stats.getSalesAmount() != null
                    ? BigDecimal.valueOf(stats.getSalesAmount()) : null;
            case "footTraffic" -> stats.getFootTraffic() != null
                    ? BigDecimal.valueOf(stats.getFootTraffic()) : null;
            case "vacancyRate" -> stats.getVacancyRate();  // 이미 % 단위
            case "closureRate" -> toPercent(stats.getClosureRate());
            case "storeCount" -> stats.getStoreCount() != null
                    ? BigDecimal.valueOf(stats.getStoreCount()) : null;
            default -> null;
        };
    }

    private BigDecimal toPercent(BigDecimal value) {
        return value != null ? value.multiply(BigDecimal.valueOf(100)) : null;
    }

    private void validateMetric(String metric) {
        if (!SUPPORTED_METRICS.contains(metric)) {
            throw AppException.badRequest("지원하지 않는 지표입니다.");
        }
    }

    private void validateOrder(String order) {
        if (!"top".equals(order) && !"bottom".equals(order)) {
            throw AppException.badRequest("지원하지 않는 정렬 기준입니다.");
        }
    }

    private static Optional<int[]> parseQuarterLabel(String label) {
        Matcher m = QUARTER_PATTERN.matcher(label);
        if (!m.matches()) {
            return Optional.empty();
        }
        return Optional.of(new int[]{Integer.parseInt(m.group(1)), Integer.parseInt(m.group(2))});
    }
}
