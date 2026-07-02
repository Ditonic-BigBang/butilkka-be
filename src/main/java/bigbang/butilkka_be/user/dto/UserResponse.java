package bigbang.butilkka_be.user.dto;

import bigbang.butilkka_be.user.User;

public record UserResponse(
        Long id,
        String name,
        boolean isOnboarded,
        StoreInfo store
) {
    public record StoreInfo(
            String regionCode,
            String regionName,
            String categoryCode,
            String categoryName,
            Double lat,
            Double lng
    ) {}

    public static UserResponse of(User user, StoreInfo store) {
        return new UserResponse(user.getId(), user.getName(), user.isOnboarded(), store);
    }
}
