package bigbang.butilkka_be.auth;

import bigbang.butilkka_be.auth.dto.AuthResponse;
import bigbang.butilkka_be.auth.dto.KakaoLoginRequest;
import bigbang.butilkka_be.auth.dto.ReissueRequest;
import bigbang.butilkka_be.auth.dto.ReissueResponse;
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

    public AuthResponse kakaoLogin(KakaoLoginRequest request) {
        KakaoUserInfo kakaoUserInfo = kakaoAuthClient.getUserInfo(request.accessToken());

        User user = userRepository.findByKakaoId(kakaoUserInfo.id())
                .orElseGet(() -> userRepository.save(
                        User.create(kakaoUserInfo.id(), kakaoUserInfo.nickname())
                ));

        String accessToken = jwtTokenProvider.generateAccessToken(String.valueOf(user.getId()));
        String refreshToken = jwtTokenProvider.generateRefreshToken(String.valueOf(user.getId()));

        refreshTokenRepository.deleteByUserId(user.getId());
        refreshTokenRepository.save(
                RefreshToken.create(user.getId(), refreshToken, jwtTokenProvider.getRefreshTokenExpiry())
        );

        return new AuthResponse(accessToken, refreshToken, user.isOnboarded());
    }

    public ReissueResponse reissue(ReissueRequest request) {
        String token = request.refreshToken();

        if (!jwtTokenProvider.validateToken(token)) {
            throw AppException.unauthorized("만료되거나 유효하지 않은 리프레시 토큰입니다. 다시 로그인해주세요");
        }

        RefreshToken stored = refreshTokenRepository.findByToken(token)
                .orElseThrow(() -> AppException.unauthorized("만료되거나 유효하지 않은 리프레시 토큰입니다. 다시 로그인해주세요"));

        if (stored.isExpired()) {
            refreshTokenRepository.delete(stored);
            throw AppException.unauthorized("만료되거나 유효하지 않은 리프레시 토큰입니다. 다시 로그인해주세요");
        }

        String subject = jwtTokenProvider.getSubject(token);
        String newAccessToken = jwtTokenProvider.generateAccessToken(subject);
        String newRefreshToken = jwtTokenProvider.generateRefreshToken(subject);

        refreshTokenRepository.delete(stored);
        refreshTokenRepository.save(
                RefreshToken.create(stored.getUserId(), newRefreshToken, jwtTokenProvider.getRefreshTokenExpiry())
        );

        return new ReissueResponse(newAccessToken, newRefreshToken);
    }
}
