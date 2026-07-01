CREATE TABLE report_signal
(
    signal_id   BIGINT       NOT NULL AUTO_INCREMENT COMMENT '요소 ID (PK)',
    report_id   BIGINT       NOT NULL COMMENT '리포트 ID (FK → reports)',
    title       VARCHAR(100) NOT NULL COMMENT '제목',
    description VARCHAR(255) NULL COMMENT '설명',
    PRIMARY KEY (signal_id),
    CONSTRAINT FK_report_signal_report FOREIGN KEY (report_id) REFERENCES reports (report_id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;
