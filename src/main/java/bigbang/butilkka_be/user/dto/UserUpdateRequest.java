package bigbang.butilkka_be.user.dto;

public record UserUpdateRequest(
        String name,
        StoreUpdatePartial store
) {
    public record StoreUpdatePartial(
            String regionCode,
            String categoryCode,
            Double lat,
            Double lng
    ) {}
}
