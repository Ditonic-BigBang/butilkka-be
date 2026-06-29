package bigbang.butilkka_be.auth;

import bigbang.butilkka_be.auth.dto.AuthResponse;
import bigbang.butilkka_be.auth.dto.ReissueRequest;
import bigbang.butilkka_be.auth.dto.ReissueResponse;
import bigbang.butilkka_be.common.response.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @Value("${app.frontend-url}")
    private String frontendUrl;

    @Value("${kakao.client-id}")
    private String kakaoClientId;

    @Value("${kakao.redirect-uri}")
    private String kakaoRedirectUri;

    @GetMapping("/kakao/login")
    public ResponseEntity<Void> kakaoLogin() {
        URI kakaoAuthUrl = UriComponentsBuilder
                .fromUriString("https://kauth.kakao.com/oauth/authorize")
                .queryParam("client_id", kakaoClientId)
                .queryParam("redirect_uri", kakaoRedirectUri)
                .queryParam("response_type", "code")
                .build().toUri();
        return ResponseEntity.status(HttpStatus.FOUND).location(kakaoAuthUrl).build();
    }

    @GetMapping("/kakao/callback")
    public ResponseEntity<Void> kakaoCallback(@RequestParam String code) {
        AuthResponse auth = authService.kakaoCallback(code);
        URI location = UriComponentsBuilder.fromUriString(frontendUrl + "/auth/kakao")
                .queryParam("accessToken", auth.accessToken())
                .queryParam("refreshToken", auth.refreshToken())
                .queryParam("isOnboarded", auth.isOnboarded())
                .build().toUri();
        return ResponseEntity.status(HttpStatus.FOUND).location(location).build();
    }

    @PostMapping("/reissue")
    public ResponseEntity<ApiResponse<ReissueResponse>> reissue(@Valid @RequestBody ReissueRequest request) {
        return ResponseEntity.ok(ApiResponse.ok("토큰 재발급 성공", authService.reissue(request)));
    }
}
