package bigbang.butilkka_be.region;

import bigbang.butilkka_be.common.exception.AppException;
import bigbang.butilkka_be.region.dto.MetricMapItem;
import bigbang.butilkka_be.region.dto.MetricMapResponse;
import bigbang.butilkka_be.stats.DistrictStats;
import bigbang.butilkka_be.stats.DistrictStatsQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class MetricMapService {

    private static final Set<String> SUPPORTED_METRICS =
            Set.of("rentRatio", "footTraffic", "vacancyRate", "closureRate", "storeCount");
    private static final Pattern QUARTER_PATTERN = Pattern.compile("(\\d{4})Q([1-4])");

    private final DistrictStatsQueryService districtStatsQueryService;

    public MetricMapResponse getMetricMap(String metric, String quarterParam) {
        validateMetric(metric);

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

        List<MetricMapItem> items = statsList.stream()
                .map(s -> toMapItem(s, metric))
                .toList();

        return new MetricMapResponse(metric, quarterLabel, items);
    }

    private MetricMapItem toMapItem(DistrictStats stats, String metric) {
        BigDecimal value = extractMetricValue(stats, metric);
        return new MetricMapItem(
                stats.getDistrictCode(),
                stats.getDistrictName(),
                stats.getDistrictName(),  // district = 구명
                value
        );
    }

    private BigDecimal extractMetricValue(DistrictStats stats, String metric) {
        return switch (metric) {
            case "rentRatio" -> stats.getSalesAmount() != null
                    ? BigDecimal.valueOf(stats.getSalesAmount()) : null;
            case "footTraffic" -> stats.getFootTraffic() != null
                    ? BigDecimal.valueOf(stats.getFootTraffic()) : null;
            case "vacancyRate" -> toPercent(stats.getVacancyRate());
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

    private static Optional<int[]> parseQuarterLabel(String label) {
        Matcher m = QUARTER_PATTERN.matcher(label);
        if (!m.matches()) {
            return Optional.empty();
        }
        return Optional.of(new int[]{Integer.parseInt(m.group(1)), Integer.parseInt(m.group(2))});
    }
}
