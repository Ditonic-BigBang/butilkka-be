package bigbang.butilkka_be.region.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.math.BigDecimal;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record MetricRankingItem(
        int rank,
        String districtCode,
        String districtName,
        String grade,
        BigDecimal value,
        BigDecimal avgOperatingYears  // closureRate일 때만 non-null
) {}
