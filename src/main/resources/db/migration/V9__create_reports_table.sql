CREATE TABLE reports
(
    report_id               BIGINT       NOT NULL AUTO_INCREMENT COMMENT '리포트 ID (PK)',
    user_id                 BIGINT       NOT NULL COMMENT '사용자 ID (FK → users)',
    region_code             VARCHAR(20)  NOT NULL COMMENT '상권 코드 (FK → regions)',
    category_code           VARCHAR(30)  NULL COMMENT '업종 코드 (FK → categories)',
    quarter                 TINYINT      NOT NULL COMMENT '분기 (1~4)',
    grade                   CHAR(1)      NOT NULL COMMENT '상권 등급 (A~E)',
    decline_type            VARCHAR(20)  NULL COMMENT '쇠퇴 유형 (성장형/순환형/쇠퇴형/정체형)',
    summary                 VARCHAR(255) NULL COMMENT '한 줄 요약',
    ai_outlook              TEXT         NULL COMMENT 'AI 종합 전망 (5~6줄)',
    decision_recommendation VARCHAR(10)  NULL COMMENT '의사결정 (버티기/이동)',
    decision_title          VARCHAR(100) NULL COMMENT '의사결정 타이틀',
    decision_description    TEXT         NULL COMMENT '의사결정 설명 (5~6줄)',
    created_at              TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (report_id),
    CONSTRAINT FK_reports_user FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT FK_reports_region FOREIGN KEY (region_code) REFERENCES regions (region_code),
    CONSTRAINT FK_reports_category FOREIGN KEY (category_code) REFERENCES categories (category_code)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;
