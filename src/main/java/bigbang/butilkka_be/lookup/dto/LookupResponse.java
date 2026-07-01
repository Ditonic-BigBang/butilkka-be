package bigbang.butilkka_be.lookup.dto;

public record LookupResponse(
        String regionCode,
        String regionName,
        String districtCode,
        String districtName
) {
    public static LookupResponse of(
            String regionCode,
            String regionName,
            String districtCode,
            String districtName) {
        return new LookupResponse(regionCode, regionName, districtCode, districtName);
    }
}
