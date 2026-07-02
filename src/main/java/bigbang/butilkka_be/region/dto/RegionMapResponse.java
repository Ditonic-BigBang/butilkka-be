package bigbang.butilkka_be.region.dto;

import java.util.List;

public record RegionMapResponse(
        String quarter,
        List<RegionMapItem> regions
) {}
