package bigbang.butilkka_be.region.dto;

import java.math.BigDecimal;

public record MetricMapItem(
        String districtCode,
        String districtName,
        String grade,
        BigDecimal value
) {}
