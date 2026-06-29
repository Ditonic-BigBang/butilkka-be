CREATE TABLE users
(
    id           BIGINT       NOT NULL AUTO_INCREMENT,
    login_id     VARCHAR(255) NOT NULL,
    password     VARCHAR(255) NOT NULL,
    name         VARCHAR(255) NOT NULL,
    is_onboarded TINYINT(1)   NOT NULL DEFAULT 0,
    created_at   DATETIME(6),
    PRIMARY KEY (id),
    CONSTRAINT UK_users_login_id UNIQUE (login_id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;
