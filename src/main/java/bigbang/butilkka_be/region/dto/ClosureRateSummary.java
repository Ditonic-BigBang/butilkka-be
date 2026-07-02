package bigbang.butilkka_be.region.dto;

import java.util.List;

public record ClosureRateSummary(
        Number value,
        Number changeRate,
        String direction,
        List<MetricTrendPoint> trend,
        Number avgOperatingYears,
        Number seoulAvgOperatingYears
) {}
