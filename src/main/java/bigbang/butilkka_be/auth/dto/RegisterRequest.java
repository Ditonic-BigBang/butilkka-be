package bigbang.butilkka_be.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
        @NotBlank(message = "이름을 입력해주세요")
        String name,

        @Email(message = "올바른 이메일 형식이 아닙니다")
        @NotBlank(message = "이메일을 입력해주세요")
        String email,

        @NotBlank(message = "비밀번호를 입력해주세요")
        @Size(min = 8, message = "비밀번호는 8자 이상이어야 합니다")
        String password
) {}
