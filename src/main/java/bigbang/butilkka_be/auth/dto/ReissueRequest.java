package bigbang.butilkka_be.auth.dto;

import jakarta.validation.constraints.NotBlank;

public record ReissueRequest(
        @NotBlank(message = "리프레시 토큰을 입력해주세요")
        String refreshToken
) {}
