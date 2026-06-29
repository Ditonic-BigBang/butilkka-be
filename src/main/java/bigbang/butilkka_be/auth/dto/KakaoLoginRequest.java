package bigbang.butilkka_be.auth.dto;

import jakarta.validation.constraints.NotBlank;

public record KakaoLoginRequest(
        @NotBlank(message = "카카오 액세스 토큰을 입력해주세요")
        String accessToken
) {}
