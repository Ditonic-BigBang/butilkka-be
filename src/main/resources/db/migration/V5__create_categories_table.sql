CREATE TABLE categories
(
    category_code VARCHAR(30) NOT NULL COMMENT '업종 코드 (예: KOREAN, CAFE)',
    category_name VARCHAR(50) NOT NULL COMMENT '업종명 (예: 한식음식점)',
    PRIMARY KEY (category_code)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;
