package bigbang.butilkka_be.auth;

import bigbang.butilkka_be.auth.dto.AuthResponse;
import bigbang.butilkka_be.auth.kakao.KakaoAuthClient;
import bigbang.butilkka_be.auth.kakao.KakaoUserInfo;
import bigbang.butilkka_be.common.exception.AppException;
import bigbang.butilkka_be.common.security.JwtTokenProvider;
import bigbang.butilkka_be.user.User;
import bigbang.butilkka_be.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
public class AuthService {

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final JwtTokenProvider jwtTokenProvider;
    private final KakaoAuthClient kakaoAuthClient;

    public AuthResponse kakaoCallback(String code) {
        String kakaoAccessToken = kakaoAuthClient.getAccessToken(code);
        KakaoUserInfo kakaoUserInfo = kakaoAuthClient.getUserInfo(kakaoAccessToken);

        User user = userRepository.findByKakaoId(kakaoUserInfo.id())
                .orElseGet(() -> userRepository.save(
                        User.create(kakaoUserInfo.id(), kakaoUserInfo.nickname())
                ));

        return issueTokens(user);
    }

    public AuthResponse refresh(String refreshToken) {
        if (!jwtTokenProvider.validateToken(refreshToken)) {
            throw AppException.unauthorized("만료되거나 유효하지 않은 리프레시 토큰입니다. 다시 로그인해주세요");
        }

        RefreshToken stored = refreshTokenRepository.findByToken(refreshToken)
                .orElseThrow(() -> AppException.unauthorized("만료되거나 유효하지 않은 리프레시 토큰입니다. 다시 로그인해주세요"));

        if (stored.isExpired()) {
            refreshTokenRepository.delete(stored);
            throw AppException.unauthorized("만료되거나 유효하지 않은 리프레시 토큰입니다. 다시 로그인해주세요");
        }

        User user = userRepository.findById(stored.getUserId())
                .orElseThrow(() -> AppException.unauthorized("존재하지 않는 사용자입니다"));

        refreshTokenRepository.delete(stored);
        return issueTokens(user);
    }

    public void logout(String refreshToken) {
        if (refreshToken == null) {
            return;
        }
        refreshTokenRepository.findByToken(refreshToken)
                .ifPresent(refreshTokenRepository::delete);
    }

    private AuthResponse issueTokens(User user) {
        String accessToken = jwtTokenProvider.generateAccessToken(String.valueOf(user.getId()));
        String refreshToken = jwtTokenProvider.generateRefreshToken(String.valueOf(user.getId()));

        refreshTokenRepository.deleteByUserId(user.getId());
        refreshTokenRepository.save(
                RefreshToken.create(user.getId(), refreshToken, jwtTokenProvider.getRefreshTokenExpiry())
        );

        return new AuthResponse(accessToken, refreshToken, user.isOnboarded());
    }
}
