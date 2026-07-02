package bigbang.butilkka_be.user.dto;

import bigbang.butilkka_be.user.User;

import java.time.LocalDate;

public record StoreResponse(
        String regionCode,
        String regionName,
        String categoryCode,
        String categoryName,
        Double lat,
        Double lng,
        String storeName,
        LocalDate storeOpenDate
) {
    public static StoreResponse of(User user, String regionName, String categoryName) {
        return new StoreResponse(
                user.getStoreRegion(),
                regionName,
                user.getCategoryCode(),
                categoryName,
                user.getStoreLat(),
                user.getStoreLng(),
                user.getStoreName(),
                user.getStoreOpenDate());
    }
}
