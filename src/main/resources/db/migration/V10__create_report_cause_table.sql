CREATE TABLE report_cause
(
    cause_id  BIGINT       NOT NULL AUTO_INCREMENT COMMENT '요소 ID (PK)',
    report_id BIGINT       NOT NULL COMMENT '리포트 ID (FK → reports)',
    title     VARCHAR(100) NOT NULL COMMENT '제목',
    level     VARCHAR(10)  NULL COMMENT '위험 단계 (높음/중간/낮음) — SIGNAL 전용',
    PRIMARY KEY (cause_id),
    CONSTRAINT FK_report_cause_report FOREIGN KEY (report_id) REFERENCES reports (report_id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;
