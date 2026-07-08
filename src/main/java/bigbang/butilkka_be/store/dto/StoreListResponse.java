package bigbang.butilkka_be.store.dto;

import bigbang.butilkka_be.store.Store;

import java.time.LocalDate;

public record StoreListResponse(
        Long storeId,
        String storeName,
        String address,
        LocalDate storeOpenDate,
        String regionCode,
        String regionName,
        String categoryCode,
        String categoryName,
        Double lat,
        Double lng,
        boolean isPrimary
) {
    public static StoreListResponse of(Store store, String regionName, String categoryName) {
        return new StoreListResponse(
                store.getId(),
                store.getStoreName(),
                store.getStoreAddress(),
                store.getStoreOpenDate(),
                store.getRegionCode(),
                regionName,
                store.getCategoryCode(),
                categoryName,
                store.getLat(),
                store.getLng(),
                store.isPrimary()
        );
    }
}
