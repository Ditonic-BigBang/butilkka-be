package bigbang.butilkka_be.region.dto;

public record RegionLookupCandidate(
        String regionCode,
        String regionName,
        String address,
        double lat,
        double lng
) {}
