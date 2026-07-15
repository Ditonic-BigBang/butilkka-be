package bigbang.butilkka_be.region.dto;

import java.util.List;

public record MetricMapResponse(
        String metric,
        String quarter,
        List<MetricMapItem> regions
) {}
