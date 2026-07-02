package bigbang.butilkka_be.region.dto;

import java.util.List;

public record RegionRankingResponse(
        String order,
        String quarter,
        List<RegionRankingItem> regions
) {}
