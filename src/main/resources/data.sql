-- 테스트 데이터 (비밀번호: password)
INSERT INTO users (login_id, password, name, is_onboarded, created_at) VALUES
    ('test_user1', '$2a$10$92IXUNpkjO0rOQ5byMi.Ye4oKoEa3Ro9llC/.og/at2.uheWG/igi', '김철수', 0, NOW()),
    ('test_user2', '$2a$10$92IXUNpkjO0rOQ5byMi.Ye4oKoEa3Ro9llC/.og/at2.uheWG/igi', '이영희', 0, NOW()),
    ('test_user3', '$2a$10$92IXUNpkjO0rOQ5byMi.Ye4oKoEa3Ro9llC/.og/at2.uheWG/igi', '박민준', 0, NOW());
