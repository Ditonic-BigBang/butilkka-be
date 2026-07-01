CREATE TABLE districts
(
    district_code VARCHAR(50) NOT NULL COMMENT '자치구 코드 (PK)',
    district_name VARCHAR(50) NULL COMMENT '자치구명 (예: 마포구)',
    PRIMARY KEY (district_code)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;
