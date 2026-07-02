package bigbang.butilkka_be.region.dto;

import java.util.List;

public record RegionDetailResponse(
        String regionCode,
        String district,
        String regionName,
        String quarter,
        DeclineGradeSummary declineGrade,
        MetricSummary rentRatio,
        MetricSummary footTraffic,
        MetricSummary vacancyRate,
        ClosureRateSummary closureRate,
        StoreCountSummary storeCount
) {
    public record DeclineGradeSummary(String current, String previous, List<GradeTrendPoint> trend) {}

    public record GradeTrendPoint(String quarter, String grade) {}
}
