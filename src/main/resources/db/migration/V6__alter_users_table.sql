ALTER TABLE users
    MODIFY COLUMN name VARCHAR(50) NOT NULL,
    ADD COLUMN store_name      VARCHAR(50)  NULL COMMENT '가게명' AFTER name,
    ADD COLUMN store_address   VARCHAR(100) NULL COMMENT '가게 주소' AFTER store_name,
    ADD COLUMN store_open_date DATE         NULL COMMENT '창업일' AFTER store_address,
    ADD COLUMN store_region    VARCHAR(20)  NULL COMMENT '가게 상권 코드 (FK → regions)' AFTER store_open_date,
    ADD COLUMN category_code   VARCHAR(30)  NULL COMMENT '업종 코드 (FK → categories)' AFTER store_region,
    ADD COLUMN sms_alert       TINYINT(1)   NOT NULL DEFAULT 0 COMMENT 'SMS 알림 연동 여부' AFTER category_code,
    ADD COLUMN auto_report     TINYINT(1)   NOT NULL DEFAULT 0 COMMENT '분기별 자동 리포트 여부' AFTER sms_alert,
    ADD COLUMN urgent_alert    TINYINT(1)   NOT NULL DEFAULT 0 COMMENT '비상신호 즉시 알림 여부' AFTER auto_report,
    ADD CONSTRAINT FK_users_store_region FOREIGN KEY (store_region) REFERENCES regions (region_code),
    ADD CONSTRAINT FK_users_category FOREIGN KEY (category_code) REFERENCES categories (category_code);
