package bigbang.butilkka_be.user;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "users")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "login_id", nullable = false, unique = true)
    private String loginId;

    @Column(nullable = false)
    private String password;

    @Column(nullable = false)
    private String name;

    @Column(name = "is_onboarded", nullable = false)
    private boolean isOnboarded = false;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    public static User create(String loginId, String encodedPassword, String name) {
        User user = new User();
        user.loginId = loginId;
        user.password = encodedPassword;
        user.name = name;
        user.isOnboarded = false;
        user.createdAt = LocalDateTime.now();
        return user;
    }
}
