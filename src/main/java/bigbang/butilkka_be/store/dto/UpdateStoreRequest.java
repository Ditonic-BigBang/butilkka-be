package bigbang.butilkka_be.store.dto;

import java.time.LocalDate;

public record UpdateStoreRequest(
        String regionCode,
        String categoryCode,
        Double lat,
        Double lng,
        String storeName,
        String storeAddress,
        LocalDate storeOpenDate,
        Boolean isPrimary
) {}
