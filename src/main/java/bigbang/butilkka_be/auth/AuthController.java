package bigbang.butilkka_be.auth;

import bigbang.butilkka_be.auth.dto.AuthResponse;
import bigbang.butilkka_be.common.exception.AppException;
import bigbang.butilkka_be.common.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private static final long ACCESS_TOKEN_MAX_AGE  = 30 * 60L;      // 30분
    private static final long REFRESH_TOKEN_MAX_AGE = 7 * 24 * 60 * 60L; // 7일
    private static final long STATE_MAX_AGE         = 5 * 60L;        // 5분

    private final AuthService authService;

    @Value("${app.frontend-url}")
    private String frontendUrl;

    @Value("${kakao.client-id}")
    private String kakaoClientId;

    @Value("${kakao.redirect-uri}")
    private String kakaoRedirectUri;

    @Value("${app.cookie.secure}")
    private boolean cookieSecure;

    @GetMapping("/kakao/login")
    public ResponseEntity<Void> kakaoLogin() {
        String state = UUID.randomUUID().toString();

        URI kakaoAuthUrl = UriComponentsBuilder
                .fromUriString("https://kauth.kakao.com/oauth/authorize")
                .queryParam("client_id", kakaoClientId)
                .queryParam("redirect_uri", kakaoRedirectUri)
                .queryParam("response_type", "code")
                .queryParam("state", state)
                .build().toUri();

        return ResponseEntity.status(HttpStatus.FOUND)
                .header(HttpHeaders.SET_COOKIE, buildCookie("oauth_state", state, STATE_MAX_AGE).toString())
                .location(kakaoAuthUrl)
                .build();
    }

    @GetMapping("/kakao/callback")
    public ResponseEntity<Void> kakaoCallback(
            @RequestParam String code,
            @RequestParam String state,
            @CookieValue(name = "oauth_state", required = false) String storedState) {

        if (storedState == null || !storedState.equals(state)) {
            return ResponseEntity.status(HttpStatus.FOUND)
                    .header(HttpHeaders.SET_COOKIE, clearCookie("oauth_state").toString())
                    .location(URI.create(frontendUrl + "/login?error=invalid_state"))
                    .build();
        }

        AuthResponse auth = authService.kakaoCallback(code);

        URI location = UriComponentsBuilder.fromUriString(frontendUrl + "/auth/kakao")
                .queryParam("success", true)
                .queryParam("isOnboarded", auth.isOnboarded())
                .build().toUri();

        return ResponseEntity.status(HttpStatus.FOUND)
                .header(HttpHeaders.SET_COOKIE, clearCookie("oauth_state").toString())
                .header(HttpHeaders.SET_COOKIE, buildCookie("access_token", auth.accessToken(), ACCESS_TOKEN_MAX_AGE).toString())
                .header(HttpHeaders.SET_COOKIE, buildCookie("refresh_token", auth.refreshToken(), REFRESH_TOKEN_MAX_AGE).toString())
                .location(location)
                .build();
    }

    @PostMapping("/refresh")
    public ResponseEntity<ApiResponse<Void>> refresh(
            @CookieValue(name = "refresh_token", required = false) String refreshToken) {
        if (refreshToken == null) {
            throw AppException.unauthorized("리프레시 토큰이 없습니다. 다시 로그인해주세요");
        }
        AuthResponse auth = authService.refresh(refreshToken);

        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, buildCookie("access_token", auth.accessToken(), ACCESS_TOKEN_MAX_AGE).toString())
                .header(HttpHeaders.SET_COOKIE, buildCookie("refresh_token", auth.refreshToken(), REFRESH_TOKEN_MAX_AGE).toString())
                .body(ApiResponse.ok("토큰 재발급 성공", null));
    }

    @PostMapping("/logout")
    public ResponseEntity<ApiResponse<Void>> logout(
            @CookieValue(name = "refresh_token", required = false) String refreshToken) {
        authService.logout(refreshToken);

        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, clearCookie("access_token").toString())
                .header(HttpHeaders.SET_COOKIE, clearCookie("refresh_token").toString())
                .body(ApiResponse.ok("로그아웃 성공", null));
    }

    private ResponseCookie buildCookie(String name, String value, long maxAgeSeconds) {
        return ResponseCookie.from(name, value)
                .httpOnly(true)
                .secure(cookieSecure)
                .sameSite("Lax")
                .path("/")
                .maxAge(maxAgeSeconds)
                .build();
    }

    private ResponseCookie clearCookie(String name) {
        return ResponseCookie.from(name, "")
                .httpOnly(true)
                .secure(cookieSecure)
                .sameSite("Lax")
                .path("/")
                .maxAge(0)
                .build();
    }
}
