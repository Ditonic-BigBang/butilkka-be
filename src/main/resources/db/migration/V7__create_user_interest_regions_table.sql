CREATE TABLE user_interest_regions
(
    id          BIGINT      NOT NULL AUTO_INCREMENT COMMENT '즐겨찾기 ID (PK)',
    user_id     BIGINT      NOT NULL COMMENT '사용자 ID (FK → users)',
    region_code VARCHAR(20) NOT NULL COMMENT '상권 코드 (FK → regions)',
    alias       VARCHAR(50) NULL COMMENT '즐겨찾기 별칭',
    sort_order  INT         NULL COMMENT '즐겨찾기 순서',
    PRIMARY KEY (id),
    CONSTRAINT FK_user_interest_regions_user FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT FK_user_interest_regions_region FOREIGN KEY (region_code) REFERENCES regions (region_code)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;
