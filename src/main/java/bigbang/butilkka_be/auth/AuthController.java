package bigbang.butilkka_be.auth;

import bigbang.butilkka_be.auth.dto.AuthResponse;
import bigbang.butilkka_be.auth.dto.KakaoLoginRequest;
import bigbang.butilkka_be.auth.dto.ReissueRequest;
import bigbang.butilkka_be.auth.dto.ReissueResponse;
import bigbang.butilkka_be.common.response.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/kakao")
    public ResponseEntity<ApiResponse<AuthResponse>> kakaoLogin(@Valid @RequestBody KakaoLoginRequest request) {
        return ResponseEntity.ok(ApiResponse.ok("로그인 성공", authService.kakaoLogin(request)));
    }

    @PostMapping("/reissue")
    public ResponseEntity<ApiResponse<ReissueResponse>> reissue(@Valid @RequestBody ReissueRequest request) {
        return ResponseEntity.ok(ApiResponse.ok("토큰 재발급 성공", authService.reissue(request)));
    }
}
