package bigbang.butilkka_be.user.dto;

import java.time.LocalDate;

public record StoreUpdateRequest(
        String regionCode,
        String categoryCode,
        Double lat,
        Double lng,
        String storeName,
        LocalDate storeOpenDate
) {}
