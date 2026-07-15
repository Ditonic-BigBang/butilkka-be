package bigbang.butilkka_be.region.dto;

import java.math.BigDecimal;

public record MetricRankingItem(
        int rank,
        String regionCode,
        String regionName,
        BigDecimal value,
        String direction
) {}
