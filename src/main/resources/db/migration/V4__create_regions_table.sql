CREATE TABLE regions
(
    region_code   VARCHAR(20)  NOT NULL COMMENT '상권 코드 (PK)',
    region_name   VARCHAR(100) NOT NULL COMMENT '상권명 (예: 가로수길)',
    district_code VARCHAR(50)  NOT NULL COMMENT '자치구 코드',
    PRIMARY KEY (region_code),
    CONSTRAINT FK_regions_district FOREIGN KEY (district_code) REFERENCES districts (district_code)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;
