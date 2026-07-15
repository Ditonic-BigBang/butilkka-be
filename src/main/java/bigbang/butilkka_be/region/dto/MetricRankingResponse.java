package bigbang.butilkka_be.region.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.math.BigDecimal;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record MetricRankingResponse(
        String metric,
        String order,
        String quarter,
        List<MetricRankingItem> regions,
        BigDecimal avgOperatingYears  // closureRate일 때만 non-null
) {}
