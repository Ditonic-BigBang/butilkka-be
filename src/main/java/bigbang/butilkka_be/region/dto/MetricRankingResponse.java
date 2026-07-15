package bigbang.butilkka_be.region.dto;

import java.util.List;

public record MetricRankingResponse(
        String metric,
        String order,
        String quarter,
        List<MetricRankingItem> districts
) {}
