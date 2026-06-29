package bigbang.butilkka_be.user.dto;

import bigbang.butilkka_be.user.User;

public record UserResponse(
        Long id,
        String name,
        boolean isOnboarded
) {
    public static UserResponse from(User user) {
        return new UserResponse(user.getId(), user.getName(), user.isOnboarded());
    }
}
