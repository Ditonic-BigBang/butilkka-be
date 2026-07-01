CREATE TABLE report_decision_reasons
(
    id        VARCHAR(36)  NOT NULL COMMENT '의사결정 근거 ID (PK, UUID)',
    report_id BIGINT       NOT NULL COMMENT '리포트 ID (FK → reports)',
    reason_1  VARCHAR(100) NULL COMMENT '근거 1',
    reason_2  VARCHAR(100) NULL COMMENT '근거 2',
    reason_3  VARCHAR(100) NULL COMMENT '근거 3',
    PRIMARY KEY (id),
    CONSTRAINT FK_report_decision_reasons_report FOREIGN KEY (report_id) REFERENCES reports (report_id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;
