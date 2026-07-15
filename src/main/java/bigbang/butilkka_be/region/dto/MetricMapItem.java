package bigbang.butilkka_be.region.dto;

import java.math.BigDecimal;

public record MetricMapItem(
        String regionCode,
        String regionName,
        String district,
        BigDecimal value
) {}
