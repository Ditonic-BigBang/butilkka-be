package bigbang.butilkka_be.auth;

import bigbang.butilkka_be.auth.dto.*;
import bigbang.butilkka_be.common.exception.AppException;
import bigbang.butilkka_be.common.security.JwtTokenProvider;
import bigbang.butilkka_be.user.User;
import bigbang.butilkka_be.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
public class AuthService {

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;

    public SignupResponse signup(RegisterRequest request) {
        if (userRepository.existsByLoginId(request.loginId())) {
            throw AppException.conflict("이미 사용 중인 아이디입니다");
        }
        User user = User.create(request.loginId(), passwordEncoder.encode(request.password()), request.name());
        userRepository.save(user);
        return new SignupResponse(user.getId(), user.getLoginId());
    }

    public AuthResponse login(LoginRequest request) {
        User user = userRepository.findByLoginId(request.loginId())
                .orElseThrow(() -> AppException.unauthorized("아이디 또는 비밀번호가 올바르지 않습니다"));
        if (!passwordEncoder.matches(request.password(), user.getPassword())) {
            throw AppException.unauthorized("아이디 또는 비밀번호가 올바르지 않습니다");
        }

        String accessToken = jwtTokenProvider.generateAccessToken(user.getLoginId());
        String refreshToken = jwtTokenProvider.generateRefreshToken(user.getLoginId());

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
