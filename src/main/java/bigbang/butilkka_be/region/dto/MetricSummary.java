package bigbang.butilkka_be.region.dto;

import java.util.List;

public record MetricSummary(
        Number value,
        Number changeRate,
        String direction,
        List<MetricTrendPoint> trend
) {}
