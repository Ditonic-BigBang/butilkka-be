-- =============================================
-- Butilkka Database Schema
-- =============================================

-- 1. 자치구 (먼저 생성 - regions에서 참조)
CREATE TABLE districts (
    district_code   VARCHAR(50)     NOT NULL    COMMENT '자치구 코드 (PK)',
    district_name   VARCHAR(50)     NULL        COMMENT '자치구 (예: 마포구)',
    PRIMARY KEY (district_code)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

-- 2. 업종
CREATE TABLE categories (
    category_code   VARCHAR(30)     NOT NULL    COMMENT '업종 코드 (예: KOREAN, CAFE)',
    category_name   VARCHAR(50)     NOT NULL    COMMENT '업종명 (예: 한식음식점)',
    PRIMARY KEY (category_code)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

-- 3. 상권 기본 정보
CREATE TABLE regions (
    region_code     VARCHAR(20)     NOT NULL    COMMENT '상권 코드 (PK)',
    region_name     VARCHAR(100)    NOT NULL    COMMENT '상권명 (예: 가로수길)',
    district_code   VARCHAR(50)     NOT NULL    COMMENT '자치구 코드',
    PRIMARY KEY (region_code),
    FOREIGN KEY (district_code) REFERENCES districts(district_code)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

-- 4. 사용자 (점주)
CREATE TABLE users (
    user_id             BIGINT          NOT NULL    AUTO_INCREMENT  COMMENT '사용자 ID (PK)',
    kakao_id            VARCHAR(50)     NOT NULL    COMMENT '카카오 소셜 ID',
    is_onboarded        BOOLEAN         NOT NULL    COMMENT '가게 정보 등록 완료 여부',
    name                VARCHAR(50)     NOT NULL    COMMENT '점주명',
    store_name          VARCHAR(50)     NULL        COMMENT '가게명',
    store_address       VARCHAR(100)    NULL        COMMENT '가게 주소',
    store_open_date     DATE            NULL        COMMENT '창업일',
    store_region        VARCHAR(20)     NULL        COMMENT '가게 상권 코드 (FK → regions)',
    category_code       VARCHAR(30)     NULL        COMMENT '업종 코드 (FK → categories)',
    sms_alert           BOOLEAN         NOT NULL    DEFAULT FALSE   COMMENT 'SMS 알림 연동 여부',
    auto_report         BOOLEAN         NOT NULL    DEFAULT FALSE   COMMENT '분기별 자동 리포트 여부',
    urgent_alert        BOOLEAN         NOT NULL    DEFAULT FALSE   COMMENT '비상신호 즉시 알림 여부',
    created_at          TIMESTAMP       NOT NULL    DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (user_id),
    FOREIGN KEY (store_region)  REFERENCES regions(region_code),
    FOREIGN KEY (category_code) REFERENCES categories(category_code)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

-- 5. 관심 상권 (즐겨찾기)
CREATE TABLE user_interest_regions (
    id              BIGINT          NOT NULL    AUTO_INCREMENT  COMMENT '즐겨찾기 ID (PK)',
    user_id         BIGINT          NOT NULL    COMMENT '사용자 ID (FK → users)',
    region_code     VARCHAR(20)     NOT NULL    COMMENT '상권 코드 (FK → regions)',
    alias           VARCHAR(50)     NULL        COMMENT '즐겨찾기 별칭',
    sort_order      INT             NULL        COMMENT '즐겨찾기 순서',
    PRIMARY KEY (id),
    FOREIGN KEY (user_id)       REFERENCES users(user_id),
    FOREIGN KEY (region_code)   REFERENCES regions(region_code)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

-- 6. 상권 통계 (분기별)
CREATE TABLE commercial_stats (
    stat_id                 BIGINT          NOT NULL    AUTO_INCREMENT  COMMENT '통계 ID (PK)',
    region_code             VARCHAR(20)     NOT NULL    COMMENT '상권 코드 (FK → regions)',
    category_code           VARCHAR(30)     NULL        COMMENT '업종 코드 (FK → categories)',
    quarter                 TINYINT         NOT NULL    COMMENT '분기 (1~4)',
    foot_traffic            INT             NULL        COMMENT '유동인구 (명)',
    foot_traffic_delta      DECIMAL(5,2)    NULL        COMMENT '유동인구 전분기 대비 변화율 (%)',
    foot_traffic_gap        BIGINT          NULL        COMMENT '유동인구 전분기 대비 수치',
    top_age_group           VARCHAR(10)     NULL        COMMENT '유동인구 최다 연령대',
    top_gender              CHAR(1)         NULL        COMMENT '유동인구 최다 성별 (M/F)',
    store_count             INT             NULL        COMMENT '점포 수 (개)',
    store_count_delta       DECIMAL(5,2)    NULL        COMMENT '점포 수 전분기 대비 변화율 (%)',
    store_count_gap         BIGINT          NULL        COMMENT '점포 수 전분기 대비 수치',
    sales_amount            BIGINT          NULL        COMMENT '매출 (원)',
    sales_delta             DECIMAL(5,2)    NULL        COMMENT '매출 전분기 대비 변화율 (%)',
    sales_gap               BIGINT          NULL        COMMENT '매출 전분기 대비 수치',
    rent_amount             BIGINT          NULL        COMMENT '임대료 (원)',
    rent_delta              DECIMAL(5,2)    NULL        COMMENT '임대료 전분기 대비 변화율 (%)',
    rent_gap                BIGINT          NULL        COMMENT '임대료 전분기 대비 수치',
    closure_rate            DECIMAL(5,2)    NULL        COMMENT '폐업률 (%)',
    closure_rate_delta      DECIMAL(5,2)    NULL        COMMENT '폐업률 전분기 대비 변화율 (%)',
    closure_rate_gap        BIGINT          NULL        COMMENT '폐업률 전분기 대비 수치',
    vacancy_rate            DECIMAL(5,2)    NULL        COMMENT '공실률 (%)',
    vacancy_rate_delta      DECIMAL(5,2)    NULL        COMMENT '공실률 전분기 대비 변화율 (%)',
    vacancy_rate_gap        BIGINT          NULL        COMMENT '공실률 전분기 대비 수치',
    avg_business_period     DECIMAL(5,2)    NULL        COMMENT '평균 영업기간 (년)',
    decline_grade           CHAR(1)         NULL        COMMENT '쇠퇴 등급 (A~E)',
    briefing                VARCHAR(255)    NULL        COMMENT 'AI 한 줄 브리핑',
    PRIMARY KEY (stat_id),
    FOREIGN KEY (region_code)   REFERENCES regions(region_code),
    FOREIGN KEY (category_code) REFERENCES categories(category_code)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

-- 7. AI 리포트
CREATE TABLE reports (
    report_id               BIGINT          NOT NULL    AUTO_INCREMENT  COMMENT '리포트 ID (PK)',
    user_id                 BIGINT          NOT NULL    COMMENT '사용자 ID (FK → users)',
    region_code             VARCHAR(20)     NOT NULL    COMMENT '상권 코드 (FK → regions)',
    category_code           VARCHAR(30)     NULL        COMMENT '업종 코드 (FK → categories)',
    quarter                 TINYINT         NOT NULL    COMMENT '분기 (1~4)',
    grade                   CHAR(1)         NOT NULL    COMMENT '상권 등급 (A~E)',
    decline_type            VARCHAR(20)     NULL        COMMENT '쇠퇴 유형 (성장형/순환형/쇠퇴형/정체형)',
    summary                 VARCHAR(255)    NULL        COMMENT '한 줄 요약',
    ai_outlook              TEXT            NULL        COMMENT 'AI 종합 전망 (5~6줄)',
    decision_recommendation VARCHAR(10)     NULL        COMMENT '의사결정 (버티기/이동)',
    decision_title          VARCHAR(100)    NULL        COMMENT '의사결정 타이틀',
    decision_description    TEXT            NULL        COMMENT '의사결정 설명 (5~6줄)',
    created_at              TIMESTAMP       NOT NULL    DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (report_id),
    FOREIGN KEY (user_id)       REFERENCES users(user_id),
    FOREIGN KEY (region_code)   REFERENCES regions(region_code),
    FOREIGN KEY (category_code) REFERENCES categories(category_code)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

-- 8. 리포트 원인분석
CREATE TABLE report_cause (
    cause_id        BIGINT          NOT NULL    AUTO_INCREMENT  COMMENT '요소 ID (PK)',
    report_id       BIGINT          NOT NULL    COMMENT '리포트 ID (FK → reports)',
    title           VARCHAR(100)    NOT NULL    COMMENT '제목',
    level           VARCHAR(10)     NULL        COMMENT '위험 단계 (높음/중간/낮음)',
    PRIMARY KEY (cause_id),
    FOREIGN KEY (report_id) REFERENCES reports(report_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

-- 9. 리포트 선행신호 요소
CREATE TABLE report_signal (
    signal_id       BIGINT          NOT NULL    AUTO_INCREMENT  COMMENT '요소 ID (PK)',
    report_id       BIGINT          NOT NULL    COMMENT '리포트 ID (FK → reports)',
    title           VARCHAR(100)    NOT NULL    COMMENT '제목',
    description     VARCHAR(255)    NULL        COMMENT '설명',
    PRIMARY KEY (signal_id),
    FOREIGN KEY (report_id) REFERENCES reports(report_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

-- 10. 리포트 의사결정 근거
CREATE TABLE report_decision_reasons (
    id              VARCHAR(36)     NOT NULL    COMMENT '의사결정 근거 ID (PK, UUID)',
    report_id       BIGINT          NOT NULL    COMMENT '리포트 ID (FK → reports)',
    reason_1        VARCHAR(100)    NULL        COMMENT '근거 1',
    reason_2        VARCHAR(100)    NULL        COMMENT '근거 2',
    reason_3        VARCHAR(100)    NULL        COMMENT '근거 3',
    PRIMARY KEY (id),
    FOREIGN KEY (report_id) REFERENCES reports(report_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

-- 11. 유사 상권 사례
CREATE TABLE report_similar_cases (
    id              VARCHAR(36)     NOT NULL    COMMENT '유사사례 ID (PK, UUID)',
    report_id       BIGINT          NOT NULL    COMMENT '리포트 ID (FK → reports)',
    region_code     VARCHAR(20)     NOT NULL    COMMENT '유사 상권 코드 (FK → regions)',
    summary         VARCHAR(255)    NULL        COMMENT '사례 요약',
    description     TEXT            NULL        COMMENT '사례 내용',
    start_year      SMALLINT        NULL        COMMENT '사례 시작 연도',
    end_year        SMALLINT        NULL        COMMENT '사례 종료 연도',
    tag1            VARCHAR(20)     NULL        COMMENT '주요 키워드',
    tag2            VARCHAR(20)     NULL        COMMENT '주요 키워드',
    tag3            VARCHAR(20)     NULL        COMMENT '주요 키워드',
    tag4            VARCHAR(20)     NULL        COMMENT '주요 키워드',
    PRIMARY KEY (id),
    FOREIGN KEY (report_id)     REFERENCES reports(report_id),
    FOREIGN KEY (region_code)   REFERENCES regions(region_code)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

-- 12. 대안 상권 추천
CREATE TABLE report_alternative_regions (
    id              BIGINT          NOT NULL    AUTO_INCREMENT  COMMENT '대안 상권 ID (PK)',
    report_id       BIGINT          NOT NULL    COMMENT '리포트 ID (FK → reports)',
    region_code     VARCHAR(20)     NOT NULL    COMMENT '대안 상권 코드 (FK → regions)',
    reason          VARCHAR(255)    NULL        COMMENT '추천 이유',
    PRIMARY KEY (id),
    FOREIGN KEY (report_id)     REFERENCES reports(report_id),
    FOREIGN KEY (region_code)   REFERENCES regions(region_code)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

-- 13. 알림
CREATE TABLE notifications (
    notification_id BIGINT          NOT NULL    AUTO_INCREMENT  COMMENT '알림 ID (PK)',
    user_id         BIGINT          NOT NULL    COMMENT '사용자 ID (FK → users)',
    category        ENUM('EMERGENCY','REPORT','SYSTEM')  NOT NULL    COMMENT '알림 카테고리',
    title           VARCHAR(255)    NULL        COMMENT '알림 제목',
    content         VARCHAR(255)    NULL        COMMENT '알림 내용',
    is_read         BOOLEAN         NOT NULL    DEFAULT FALSE   COMMENT '읽음 여부',
    sent_at         TIMESTAMP       NOT NULL    DEFAULT CURRENT_TIMESTAMP   COMMENT '발송 일시',
    PRIMARY KEY (notification_id),
    FOREIGN KEY (user_id) REFERENCES users(user_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;
