package bigbang.butilkka_be.region.dto;

import java.util.List;

public record StoreCountSummary(
        Number value,
        Number changeCount,
        String direction,
        List<MetricTrendPoint> trend,
        List<CategoryCount> categoryDistribution
) {}
