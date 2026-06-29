package bigbang.butilkka_be.auth.dto;

public record AuthResponse(String accessToken, String refreshToken, boolean isOnboarded) {}
