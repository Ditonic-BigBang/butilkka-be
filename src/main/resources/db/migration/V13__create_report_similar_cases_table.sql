CREATE TABLE report_similar_cases
(
    id          VARCHAR(36)  NOT NULL COMMENT '유사사례 ID (PK, UUID)',
    report_id   BIGINT       NOT NULL COMMENT '리포트 ID (FK → reports)',
    region_code VARCHAR(20)  NOT NULL COMMENT '유사 상권 코드 (FK → regions)',
    summary     VARCHAR(255) NULL COMMENT '사례 요약',
    description TEXT         NULL COMMENT '사례 내용',
    start_year  SMALLINT     NULL COMMENT '사례 시작 연도',
    end_year    SMALLINT     NULL COMMENT '사례 종료 연도',
    tag1        VARCHAR(20)  NULL COMMENT '주요 키워드',
    tag2        VARCHAR(20)  NULL COMMENT '주요 키워드',
    tag3        VARCHAR(20)  NULL COMMENT '주요 키워드',
    tag4        VARCHAR(20)  NULL COMMENT '주요 키워드',
    PRIMARY KEY (id),
    CONSTRAINT FK_report_similar_cases_report FOREIGN KEY (report_id) REFERENCES reports (report_id),
    CONSTRAINT FK_report_similar_cases_region FOREIGN KEY (region_code) REFERENCES regions (region_code)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;
