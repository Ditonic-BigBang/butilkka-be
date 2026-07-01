CREATE TABLE report_alternative_regions
(
    id          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '대안 상권 ID (PK)',
    report_id   BIGINT       NOT NULL COMMENT '리포트 ID (FK → reports)',
    region_code VARCHAR(20)  NOT NULL COMMENT '대안 상권 코드 (FK → regions)',
    reason      VARCHAR(255) NULL COMMENT '추천 이유',
    PRIMARY KEY (id),
    CONSTRAINT FK_report_alternative_regions_report FOREIGN KEY (report_id) REFERENCES reports (report_id),
    CONSTRAINT FK_report_alternative_regions_region FOREIGN KEY (region_code) REFERENCES regions (region_code)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;
