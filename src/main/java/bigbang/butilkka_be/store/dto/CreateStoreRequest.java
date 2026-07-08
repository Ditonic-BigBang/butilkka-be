package bigbang.butilkka_be.store.dto;

import java.time.LocalDate;

public record CreateStoreRequest(
        String regionCode,
        String categoryCode,
        Double lat,
        Double lng,
        String storeName,
        String storeAddress,
        LocalDate storeOpenDate
) {}
