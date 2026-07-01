CREATE TABLE notifications
(
    notification_id BIGINT                                NOT NULL AUTO_INCREMENT COMMENT '알림 ID (PK)',
    user_id          BIGINT                                NOT NULL COMMENT '사용자 ID (FK → users)',
    category          ENUM ('EMERGENCY','REPORT','SYSTEM') NOT NULL COMMENT '알림 카테고리',
    title              VARCHAR(255)                        NULL COMMENT '알림 제목',
    content            VARCHAR(255)                        NULL COMMENT '알림 내용',
    is_read            TINYINT(1)                          NOT NULL DEFAULT 0 COMMENT '읽음 여부',
    sent_at            DATETIME(6)                         NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '발송 일시',
    PRIMARY KEY (notification_id),
    CONSTRAINT FK_notifications_user FOREIGN KEY (user_id) REFERENCES users (id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;
