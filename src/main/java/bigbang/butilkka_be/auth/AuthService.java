package bigbang.butilkka_be.auth;

import bigbang.butilkka_be.auth.dto.AuthResponse;
import bigbang.butilkka_be.auth.dto.LoginRequest;
import bigbang.butilkka_be.auth.dto.RegisterRequest;
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
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;

    public AuthResponse register(RegisterRequest request) {
        if (userRepository.existsByEmail(request.email())) {
            throw new IllegalArgumentException("이미 사용 중인 이메일입니다");
        }
        User user = User.create(request.name(), request.email(), passwordEncoder.encode(request.password()));
        userRepository.save(user);
        return AuthResponse.of(jwtTokenProvider.generateToken(user.getEmail()));
    }

    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest request) {
        User user = userRepository.findByEmail(request.email())
                .orElseThrow(() -> new IllegalArgumentException("이메일 또는 비밀번호가 올바르지 않습니다"));
        if (!passwordEncoder.matches(request.password(), user.getPassword())) {
            throw new IllegalArgumentException("이메일 또는 비밀번호가 올바르지 않습니다");
        }
        return AuthResponse.of(jwtTokenProvider.generateToken(user.getEmail()));
    }
}
