-- =============================================
-- Butilkka Database Schema
-- Auto-generated from migration files
-- =============================================

-- V1: users
CREATE TABLE users
(
    id           BIGINT     NOT NULL AUTO_INCREMENT,
    kakao_id     BIGINT     NOT NULL,
    name         VARCHAR(255) NOT NULL,
    is_onboarded TINYINT(1) NOT NULL DEFAULT 0,
    created_at   DATETIME(6),
    PRIMARY KEY (id),
    CONSTRAINT UK_users_kakao_id UNIQUE (kakao_id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;

-- V2: refresh_tokens
CREATE TABLE refresh_tokens
(
    id         BIGINT       NOT NULL AUTO_INCREMENT,
    user_id    BIGINT       NOT NULL,
    token      VARCHAR(512) NOT NULL,
    expires_at DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT UK_refresh_tokens_token UNIQUE (token)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;

-- V3: districts
CREATE TABLE districts
(
    district_code VARCHAR(50) NOT NULL COMMENT '자치구 코드 (PK)',
    district_name VARCHAR(50) NULL COMMENT '자치구명 (예: 마포구)',
    PRIMARY KEY (district_code)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;

-- V4: regions
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

-- V5: categories
CREATE TABLE categories
(
    category_code VARCHAR(30) NOT NULL COMMENT '업종 코드 (예: KOREAN, CAFE)',
    category_name VARCHAR(50) NOT NULL COMMENT '업종명 (예: 한식음식점)',
    PRIMARY KEY (category_code)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;

-- V6: alter users
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

-- V7: user_interest_regions
CREATE TABLE user_interest_regions
(
    id          BIGINT      NOT NULL AUTO_INCREMENT COMMENT '즐겨찾기 ID (PK)',
    user_id     BIGINT      NOT NULL COMMENT '사용자 ID (FK → users)',
    region_code VARCHAR(20) NOT NULL COMMENT '상권 코드 (FK → regions)',
    alias       VARCHAR(50) NULL COMMENT '즐겨찾기 별칭',
    sort_order  INT         NULL COMMENT '즐겨찾기 순서',
    PRIMARY KEY (id),
    CONSTRAINT FK_user_interest_regions_user FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT FK_user_interest_regions_region FOREIGN KEY (region_code) REFERENCES regions (region_code)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;

-- V8: commercial_stats
CREATE TABLE commercial_stats
(
    stat_id              BIGINT       NOT NULL AUTO_INCREMENT COMMENT '통계 ID (PK)',
    region_code          VARCHAR(20)  NOT NULL COMMENT '상권 코드 (FK → regions)',
    category_code        VARCHAR(30)  NULL COMMENT '업종 코드 (FK → categories)',
    quarter               TINYINT      NOT NULL COMMENT '분기 (1~4)',
    foot_traffic          INT          NULL COMMENT '유동인구 (명)',
    foot_traffic_delta    DECIMAL(5,2) NULL COMMENT '유동인구 전분기 대비 변화율 (%)',
    foot_traffic_gap      BIGINT       NULL COMMENT '유동인구 전분기 대비 수치',
    top_age_group         VARCHAR(10)  NULL COMMENT '유동인구 최다 연령대',
    top_gender            CHAR(1)      NULL COMMENT '유동인구 최다 성별 (M/F)',
    store_count            INT          NULL COMMENT '점포 수 (개)',
    store_count_delta      DECIMAL(5,2) NULL COMMENT '점포 수 전분기 대비 변화율 (%)',
    store_count_gap        BIGINT       NULL COMMENT '점포 수 전분기 대비 수치',
    sales_amount           BIGINT       NULL COMMENT '매출 (원)',
    sales_delta            DECIMAL(5,2) NULL COMMENT '매출 전분기 대비 변화율 (%)',
    sales_gap               BIGINT       NULL COMMENT '매출 전분기 대비 수치',
    rent_amount              BIGINT       NULL COMMENT '임대료 (원)',
    rent_delta                DECIMAL(5,2) NULL COMMENT '임대료 전분기 대비 변화율 (%)',
    rent_gap                   BIGINT       NULL COMMENT '임대료 전분기 대비 수치',
    closure_rate                DECIMAL(5,2) NULL COMMENT '폐업률 (%)',
    closure_rate_delta           DECIMAL(5,2) NULL COMMENT '폐업률 전분기 대비 변화율 (%)',
    closure_rate_gap              BIGINT       NULL COMMENT '폐업률 전분기 대비 수치',
    vacancy_rate                   DECIMAL(5,2) NULL COMMENT '공실률 (%)',
    vacancy_rate_delta              DECIMAL(5,2) NULL COMMENT '공실률 전분기 대비 변화율 (%)',
    vacancy_rate_gap                 BIGINT       NULL COMMENT '공실률 전분기 대비 수치',
    avg_business_period               DECIMAL(5,2) NULL COMMENT '평균 영업기간 (년)',
    decline_grade                      CHAR(1)      NULL COMMENT '쇠퇴 등급 (A~E)',
    briefing                            VARCHAR(255) NULL COMMENT 'AI 한 줄 브리핑',
    PRIMARY KEY (stat_id),
    CONSTRAINT FK_commercial_stats_region FOREIGN KEY (region_code) REFERENCES regions (region_code),
    CONSTRAINT FK_commercial_stats_category FOREIGN KEY (category_code) REFERENCES categories (category_code)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;

-- V9: reports
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

-- V10: report_cause
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

-- V11: report_signal
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

-- V12: report_decision_reasons
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

-- V13: report_similar_cases
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

-- V14: report_alternative_regions
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

-- V15: notifications
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
