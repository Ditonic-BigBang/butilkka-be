# 지도/상권 화면 API Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 지도/상권 화면의 API 7개(지도 색상, 쇠퇴등급 Top5, 상권검색, 상권상세, 관심상권 추가/목록/삭제)를 구현하고, 이를 위해 `commercial_stats`에 `year` 컬럼을 추가하고 12분기 추이 데이터를 확보한다.

**Architecture:** `commercial_stats`의 "최신 분기 조회"/"이력 조회" 로직을 `CommercialStatsQueryService`(stats 패키지)로 공유하고, `region` 패키지에 새 컨트롤러 2개(`RegionQueryController`, `RegionDetailController`)와 `user` 패키지에 `FavoriteController`를 추가한다. 관심 상권은 신규 엔티티 없이 기존 `UserInterestRegion`을 재사용한다.

**Tech Stack:** Spring Boot 4.1, Spring Data JPA, Flyway, MySQL 8.0 (Docker), JUnit5 + Mockito + AssertJ + MockMvc.

## Global Constraints

- 모든 신규 에러는 기존 `AppException`(badRequest/notFound/conflict) + `GlobalExceptionHandler` 컨벤션을 따른다.
- 모든 신규 응답은 `ApiResponse.ok(message, data)` / `ApiResponse.created(message, data)` 포맷을 따른다.
- 신규 엔드포인트는 `SecurityConfig`의 `anyRequest().authenticated()`에 자동 포함되므로 `SecurityConfig` 변경은 불필요하다.
- 상권 상세 API의 URI는 스펙 그대로 `/api/v1/districts/{districtsCode}`를 사용하되, path variable은 실제로는 상권코드(regionCode)로 취급한다.
- 테스트는 실제 DB 없이 동작해야 한다 (서비스 테스트는 Mockito, 컨트롤러 테스트는 `@WebMvcTest` + `@MockitoBean`, `@AutoConfigureMockMvc(addFilters = false)`). 단, `@AuthenticationPrincipal`을 사용하는 컨트롤러(Task 8의 `FavoriteController`)는 `addFilters = false`를 쓰지 않고 대신 `@Import(SecurityConfig.class)` + 기본 `@AutoConfigureMockMvc`를 사용한다 (해당 Task의 Step 7 참고).
- 이 플랜은 별도로 진행 중인 온보딩 플랜(`2026-07-02-onboarding-api-region-migration.md`)과 파일이 겹치지 않도록 설계되었다. 단, DB 마이그레이션 버전 번호는 전역 순서이므로, **이 플랜의 마이그레이션(V20, V21)은 온보딩 플랜의 V19가 실제 DB에 적용된 뒤에 적용한다** (파일 작성/커밋은 순서 상관없지만, `./gradlew bootRun`으로 DB에 반영하는 시점은 V19 이후로 미룬다).

---

### Task 1: `commercial_stats`에 `year` 컬럼 추가 (V20)

**Files:**
- Modify: `src/main/java/bigbang/butilkka_be/stats/CommercialStats.java`
- Create: `src/main/resources/db/migration/V20__add_year_to_commercial_stats.sql`

**Interfaces:**
- Produces: `CommercialStats.getYear(): Integer` — 이후 모든 태스크에서 사용.

- [ ] **Step 1: 마이그레이션 작성**

`src/main/resources/db/migration/V20__add_year_to_commercial_stats.sql`:

```sql
ALTER TABLE commercial_stats
    ADD COLUMN year SMALLINT NOT NULL DEFAULT 2026 COMMENT '연도' AFTER quarter;

ALTER TABLE commercial_stats
    ALTER COLUMN year DROP DEFAULT;
```

(`DEFAULT 2026`으로 추가하면 기존 20개 행이 자동으로 2026년으로 백필되고, 이후 `DROP DEFAULT`로 향후 INSERT는 반드시 명시하도록 만든다.)

- [ ] **Step 2: `CommercialStats` 엔티티에 필드 추가**

`src/main/java/bigbang/butilkka_be/stats/CommercialStats.java`의 `quarter` 필드 선언 바로 뒤에 추가:

```java
    @Column(nullable = false, columnDefinition = "SMALLINT")
    private Integer year;
```

- [ ] **Step 3: Docker MySQL 확인 후 앱 실행해 마이그레이션 적용 확인**

Run: `docker compose up -d mysql` (이미 떠 있으면 스킵. 단, 온보딩 플랜의 V19가 아직 적용 전이라면 먼저 그쪽을 완료해야 함 — Global Constraints 참고)
Run: `DB_URL="jdbc:mysql://localhost:3307/butilkka?serverTimezone=Asia/Seoul&characterEncoding=UTF-8" ./gradlew bootRun --console=plain`
Expected 로그: `Migrating schema \`butilkka\` to version "20 - add year to commercial stats"`, `Started ButilkkaBeApplication`

- [ ] **Step 4: DB 확인**

Run: `docker exec butilkka-be-mysql-1 mysql -uroot -p1234 -e "SELECT DISTINCT year FROM butilkka.commercial_stats;"`
Expected: `2026` 한 줄만 출력

앱을 종료한다.

- [ ] **Step 5: 커밋**

```bash
git add src/main/java/bigbang/butilkka_be/stats/CommercialStats.java src/main/resources/db/migration/V20__add_year_to_commercial_stats.sql
git commit -m "Add year column to commercial_stats"
```

---

### Task 2: 12분기 히스토리 + 지도/랭킹용 상권 12곳 시드 (V21)

**Files:**
- Create: `src/main/resources/db/migration/V21__seed_stats_history_and_breadth.sql`

**Interfaces:**
- Produces: 아래 15개 상권코드에 대한 `commercial_stats` 데이터 (Task 3~7에서 조회 대상):
  - 히어로(12분기): `1168064000`(역삼1동/CS100001), `1144066000`(서교동/CS100006), `1120065000`(성수1가1동/CS200001)
  - 브레드스(2026Q4만): `1117065000`(이태원1동/CS100003), `1156054000`(여의동/CS300001), `1165052000`(서초2동/CS100002), `1144071000`(연남동/CS100005), `1168054500`(압구정동/CS400001), `1168056500`(청담동/CS100009), `1111060000`(가회동/CS200002), `1117068500`(한남동/CS400003), `1171067000`(잠실2동/CS100008), `1144068000`(합정동/CS100010), `1114054000`(회현동/CS300002), `1121571000`(화양동/CS200003)

- [ ] **Step 1: 마이그레이션 작성**

`src/main/resources/db/migration/V21__seed_stats_history_and_breadth.sql`:

```sql
-- 히어로 상권 3곳의 2024~2025년 데이터 (2026년은 V17에 이미 존재)

-- 역삼1동(강남역)/한식 (기존 2026: Q1 B, Q2 B, Q3 C, Q4 A)
INSERT INTO commercial_stats (region_code, category_code, quarter, year, foot_traffic, foot_traffic_delta, foot_traffic_gap, top_age_group, top_gender, store_count, store_count_delta, store_count_gap, sales_amount, sales_delta, sales_gap, rent_amount, rent_delta, rent_gap, closure_rate, closure_rate_delta, closure_rate_gap, vacancy_rate, vacancy_rate_delta, vacancy_rate_gap, avg_business_period, decline_grade, briefing) VALUES
('1168064000', 'CS100001', 1, 2024, 100000, 0.0, 0, '30대', 'M', 480, 0.0, 0, 6000000000, 0.0, 0, 40000000, 0.0, 0, 5.5, 0.0, 0, 4.5, 0.0, 0, 2.5, 'D', '경쟁 심화로 쇠퇴 국면 시작'),
('1168064000', 'CS100001', 2, 2024, 105000, 5.0, 5000, '30대', 'M', 475, -1.0, -5, 6300000000, 5.0, 300000000, 40500000, 1.3, 500000, 5.2, -0.3, 0, 4.2, -0.3, 0, 2.6, 'C', '소폭 회복세'),
('1168064000', 'CS100001', 3, 2024, 102000, -2.9, -3000, '30대', 'M', 470, -1.1, -5, 6100000000, -3.2, -200000000, 41000000, 1.2, 500000, 5.6, 0.4, 0, 4.5, 0.3, 0, 2.6, 'C', '여름 비수기 조정'),
('1168064000', 'CS100001', 4, 2024, 110000, 7.8, 8000, '30대', 'F', 478, 1.7, 8, 6800000000, 11.5, 700000000, 41500000, 1.2, 500000, 5.0, -0.6, -1, 4.0, -0.5, -1, 2.8, 'B', '연말 특수로 반등'),
('1168064000', 'CS100001', 1, 2025, 113000, 2.7, 3000, '30대', 'F', 465, -2.7, -13, 7100000000, 4.4, 300000000, 42000000, 1.2, 500000, 4.8, -0.2, -1, 3.8, -0.2, 0, 3.0, 'B', '완만한 성장 지속'),
('1168064000', 'CS100001', 2, 2025, 118000, 4.4, 5000, '20대', 'F', 460, -1.1, -5, 7500000000, 5.6, 400000000, 43000000, 2.4, 1000000, 4.6, -0.2, 0, 3.6, -0.2, 0, 3.1, 'A', '매출 호조로 최상위 등급'),
('1168064000', 'CS100001', 3, 2025, 115000, -2.5, -3000, '20대', 'F', 455, -1.1, -5, 7300000000, -2.7, -200000000, 43500000, 1.2, 500000, 4.9, 0.3, 0, 3.7, 0.1, 0, 3.2, 'B', '비수기 소폭 조정'),
('1168064000', 'CS100001', 4, 2025, 121000, 5.2, 6000, '30대', 'F', 452, -0.7, -3, 7900000000, 8.2, 600000000, 44000000, 1.1, 500000, 4.6, -0.3, 0, 3.3, -0.4, 0, 3.3, 'B', '연말 상승세');

-- 서교동(홍대입구역)/카페 (기존 2026: Q1 C, Q2 B, Q3 C, Q4 B)
INSERT INTO commercial_stats (region_code, category_code, quarter, year, foot_traffic, foot_traffic_delta, foot_traffic_gap, top_age_group, top_gender, store_count, store_count_delta, store_count_gap, sales_amount, sales_delta, sales_gap, rent_amount, rent_delta, rent_gap, closure_rate, closure_rate_delta, closure_rate_gap, vacancy_rate, vacancy_rate_delta, vacancy_rate_gap, avg_business_period, decline_grade, briefing) VALUES
('1144066000', 'CS100006', 1, 2024, 150000, 0.0, 0, '20대', 'F', 290, 0.0, 0, 3200000000, 0.0, 0, 33000000, 0.0, 0, 7.0, 0.0, 0, 6.0, 0.0, 0, 2.0, 'C', '카페 경쟁 심화 초입'),
('1144066000', 'CS100006', 2, 2024, 158000, 5.3, 8000, '20대', 'F', 295, 1.7, 5, 3400000000, 6.3, 200000000, 33500000, 1.5, 500000, 6.8, -0.2, 0, 5.8, -0.2, 0, 2.1, 'C', '관광객 소폭 증가'),
('1144066000', 'CS100006', 3, 2024, 152000, -3.8, -6000, '20대', 'F', 292, -1.0, -3, 3300000000, -2.9, -100000000, 34000000, 1.5, 500000, 7.1, 0.3, 0, 6.1, 0.3, 0, 2.0, 'C', '여름 비수기'),
('1144066000', 'CS100006', 4, 2024, 165000, 8.6, 13000, '20대', 'F', 300, 2.7, 8, 3700000000, 12.1, 400000000, 34500000, 1.5, 500000, 6.5, -0.6, -1, 5.5, -0.6, -1, 2.3, 'B', '연말 특수'),
('1144066000', 'CS100006', 1, 2025, 168000, 1.8, 3000, '20대', 'F', 298, -0.7, -2, 3800000000, 2.7, 100000000, 35000000, 1.4, 500000, 6.3, -0.2, 0, 5.3, -0.2, 0, 2.4, 'B', '완만한 상승'),
('1144066000', 'CS100006', 2, 2025, 175000, 4.2, 7000, '20대', 'M', 305, 2.3, 7, 4000000000, 5.3, 200000000, 36000000, 2.9, 1000000, 6.0, -0.3, 0, 5.0, -0.3, 0, 2.5, 'A', '관광객 유입 증가로 호조'),
('1144066000', 'CS100006', 3, 2025, 170000, -2.9, -5000, '20대', 'F', 300, -1.6, -5, 3900000000, -2.5, -100000000, 36500000, 1.4, 500000, 6.3, 0.3, 0, 5.2, 0.2, 0, 2.6, 'B', '비수기 조정'),
('1144066000', 'CS100006', 4, 2025, 178000, 4.7, 8000, '20대', 'F', 310, 3.3, 10, 4100000000, 5.1, 200000000, 37000000, 1.4, 500000, 5.9, -0.4, 0, 4.8, -0.4, 0, 2.7, 'B', '연말 회복세');

-- 성수1가1동(성수동)/미용실 (기존 2026: Q1 A, Q2 A, Q3 B, Q4 A)
INSERT INTO commercial_stats (region_code, category_code, quarter, year, foot_traffic, foot_traffic_delta, foot_traffic_gap, top_age_group, top_gender, store_count, store_count_delta, store_count_gap, sales_amount, sales_delta, sales_gap, rent_amount, rent_delta, rent_gap, closure_rate, closure_rate_delta, closure_rate_gap, vacancy_rate, vacancy_rate_delta, vacancy_rate_gap, avg_business_period, decline_grade, briefing) VALUES
('1120065000', 'CS200001', 1, 2024, 55000, 0.0, 0, '30대', 'F', 70, 0.0, 0, 1200000000, 0.0, 0, 25000000, 0.0, 0, 4.0, 0.0, 0, 3.5, 0.0, 0, 3.0, 'C', '성수동 개발 초기'),
('1120065000', 'CS200001', 2, 2024, 60000, 9.1, 5000, '30대', 'F', 75, 7.1, 5, 1300000000, 8.3, 100000000, 26000000, 4.0, 1000000, 3.8, -0.2, 0, 3.3, -0.2, 0, 3.2, 'B', '핫플레이스 조짐'),
('1120065000', 'CS200001', 3, 2024, 58000, -3.3, -2000, '30대', 'F', 72, -4.0, -3, 1250000000, -3.8, -50000000, 26500000, 1.9, 500000, 4.0, 0.2, 0, 3.6, 0.3, 0, 3.1, 'B', '여름 소폭 조정'),
('1120065000', 'CS200001', 4, 2024, 65000, 12.1, 7000, '20대', 'F', 78, 8.3, 6, 1450000000, 16.0, 200000000, 27500000, 3.8, 1000000, 3.5, -0.5, 0, 3.0, -0.6, 0, 3.3, 'B', '연말 급성장'),
('1120065000', 'CS200001', 1, 2025, 68000, 4.6, 3000, '20대', 'F', 82, 5.1, 4, 1550000000, 6.9, 100000000, 28500000, 3.6, 1000000, 3.3, -0.2, 0, 2.8, -0.2, 0, 3.6, 'B', '프리미엄화 진행'),
('1120065000', 'CS200001', 2, 2025, 72000, 5.9, 4000, '20대', 'F', 88, 7.3, 6, 1700000000, 9.7, 150000000, 30000000, 5.3, 1500000, 3.0, -0.3, 0, 2.5, -0.3, 0, 3.9, 'A', '성수동 핫플 효과 본격화'),
('1120065000', 'CS200001', 3, 2025, 70000, -2.8, -2000, '20대', 'F', 85, -3.4, -3, 1650000000, -2.9, -50000000, 30500000, 1.7, 500000, 3.2, 0.2, 0, 2.7, 0.2, 0, 3.8, 'B', '비수기 조정'),
('1120065000', 'CS200001', 4, 2025, 73000, 4.3, 3000, '20대', 'F', 90, 5.9, 5, 1800000000, 9.1, 150000000, 31000000, 1.6, 500000, 3.0, -0.2, 0, 2.4, -0.3, 0, 4.2, 'A', '프리미엄 상권으로 정착');

-- 지도/랭킹 화면용 상권 12곳 (2026년 4분기 데이터만)
INSERT INTO commercial_stats (region_code, category_code, quarter, year, foot_traffic, foot_traffic_delta, foot_traffic_gap, top_age_group, top_gender, store_count, store_count_delta, store_count_gap, sales_amount, sales_delta, sales_gap, rent_amount, rent_delta, rent_gap, closure_rate, closure_rate_delta, closure_rate_gap, vacancy_rate, vacancy_rate_delta, vacancy_rate_gap, avg_business_period, decline_grade, briefing) VALUES
('1117065000', 'CS100003', 4, 2026, 90000, 2.0, 1800, '30대', 'M', 150, 1.0, 1, 2500000000, 3.0, 70000000, 35000000, 1.5, 500000, 4.0, 0.2, 0, 3.5, 0.1, 0, 3.5, 'B', '외국인 관광객 유입으로 안정적 성장'),
('1156054000', 'CS300001', 4, 2026, 200000, 1.5, 3000, '30대', 'M', 60, 0.5, 0, 1800000000, 2.0, 35000000, 50000000, 1.0, 500000, 2.5, -0.1, 0, 1.8, -0.1, 0, 5.5, 'A', '오피스 상권 특성상 안정적 수요'),
('1165052000', 'CS100002', 4, 2026, 85000, -1.5, -1300, '30대', 'M', 110, -1.0, -1, 2000000000, -2.0, -40000000, 42000000, 0.5, 200000, 4.5, 0.3, 0, 3.8, 0.2, 0, 3.2, 'C', '경쟁 심화로 소폭 조정'),
('1144071000', 'CS100005', 4, 2026, 130000, 6.5, 8000, '20대', 'F', 95, 4.5, 4, 1600000000, 7.0, 100000000, 30000000, 3.0, 900000, 3.5, -0.5, -1, 2.8, -0.5, -1, 3.0, 'A', 'SNS 인기로 급성장'),
('1168054500', 'CS400001', 4, 2026, 70000, -3.0, -2200, '40대', 'F', 130, -2.5, -3, 3500000000, -4.0, -140000000, 55000000, -1.0, -500000, 6.5, 0.8, 3, 7.0, 1.0, 4, 2.5, 'D', '명품거리 이동으로 매출 감소'),
('1168056500', 'CS100009', 4, 2026, 60000, -5.5, -3500, '30대', 'M', 80, -4.0, -3, 1700000000, -6.5, -120000000, 48000000, -1.5, -700000, 8.0, 1.5, 6, 8.5, 1.8, 6, 2.0, 'E', '심야 유흥 규제 강화로 쇠퇴'),
('1111060000', 'CS200002', 4, 2026, 45000, 3.0, 1300, '20대', 'F', 55, 2.0, 1, 900000000, 4.0, 35000000, 28000000, 1.5, 400000, 3.0, -0.2, 0, 2.5, -0.2, 0, 4.0, 'B', '한옥마을 관광 수요 꾸준'),
('1117068500', 'CS400003', 4, 2026, 55000, 1.0, 500, '30대', 'F', 65, 0.5, 0, 1500000000, 1.5, 22000000, 45000000, 0.5, 200000, 4.2, 0.1, 0, 3.5, 0.1, 0, 3.8, 'C', '고급 상권 특성상 안정적'),
('1171067000', 'CS100008', 4, 2026, 110000, 2.5, 2700, '10대', 'F', 140, 1.5, 2, 1400000000, 3.0, 40000000, 29000000, 1.0, 300000, 4.8, 0.3, 0, 4.0, 0.3, 0, 2.8, 'C', '주거단지 배후수요로 안정적'),
('1144068000', 'CS100010', 4, 2026, 140000, 4.0, 5300, '20대', 'M', 85, 2.0, 2, 1900000000, 5.0, 90000000, 34000000, 2.0, 700000, 3.8, -0.3, -1, 3.0, -0.3, -1, 3.1, 'B', '젊은층 유입 증가로 호조'),
('1114054000', 'CS300002', 4, 2026, 50000, -2.0, -1000, '50대', 'M', 40, -1.5, -1, 700000000, -2.5, -18000000, 20000000, -0.5, -100000, 5.5, 0.6, 2, 6.0, 0.8, 3, 3.5, 'D', '명동 상권 침체 영향'),
('1121571000', 'CS200003', 4, 2026, 65000, 5.0, 3100, '20대', 'F', 50, 3.5, 2, 1100000000, 6.0, 62000000, 27000000, 2.5, 650000, 3.2, -0.4, 0, 2.6, -0.4, 0, 3.3, 'A', '대학가 상권 특성상 젊은 고객층 유입');
```

- [ ] **Step 2: 앱 실행해 마이그레이션 적용 확인** (온보딩 플랜 V19가 이미 적용된 이후에 실행할 것)

Run: `docker compose up -d mysql`
Run: `DB_URL="jdbc:mysql://localhost:3307/butilkka?serverTimezone=Asia/Seoul&characterEncoding=UTF-8" ./gradlew bootRun --console=plain`
Expected 로그: `Migrating schema \`butilkka\` to version "21 - seed stats history and breadth"`, `Started ButilkkaBeApplication`

- [ ] **Step 3: DB 확인**

Run: `docker exec butilkka-be-mysql-1 mysql -uroot -p1234 -e "SELECT COUNT(*) FROM butilkka.commercial_stats WHERE region_code='1168064000';"`
Expected: `12`

Run: `docker exec butilkka-be-mysql-1 mysql -uroot -p1234 -e "SELECT COUNT(DISTINCT region_code) FROM butilkka.commercial_stats;"`
Expected: `17` (기존 5개 + 신규 12개)

앱을 종료한다.

- [ ] **Step 4: 커밋**

```bash
git add src/main/resources/db/migration/V21__seed_stats_history_and_breadth.sql
git commit -m "Seed 12-quarter history for hero regions and breadth data for 12 more regions"
```

---

### Task 3: `CommercialStatsQueryService` — 최신/이력 조회 공용 로직

**Files:**
- Modify: `src/main/java/bigbang/butilkka_be/stats/CommercialStatsRepository.java`
- Create: `src/main/java/bigbang/butilkka_be/stats/CommercialStatsQueryService.java`
- Test: `src/test/java/bigbang/butilkka_be/stats/CommercialStatsQueryServiceTest.java`

**Interfaces:**
- Produces:
  - `CommercialStatsQueryService.latestPerRegion(): List<CommercialStats>` (Task 4에서 사용)
  - `CommercialStatsQueryService.latestForRegion(String regionCode): Optional<CommercialStats>` (Task 8에서 사용)
  - `CommercialStatsQueryService.historyForRegion(String regionCode): List<CommercialStats>` (Task 7에서 사용, 연도·분기 오름차순)
  - `CommercialStatsQueryService.forQuarter(int year, int quarter): List<CommercialStats>` (Task 4, 5에서 사용)
  - `CommercialStatsQueryService.parseQuarterLabel(String label): Optional<int[]>` (static, `{year, quarter}` 반환, 형식 `YYYYQ[1-4]`이 아니면 empty)

- [ ] **Step 1: Repository에 쿼리 메서드 추가**

`src/main/java/bigbang/butilkka_be/stats/CommercialStatsRepository.java` 전체를 아래로 교체:

```java
package bigbang.butilkka_be.stats;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CommercialStatsRepository extends JpaRepository<CommercialStats, Long> {
    List<CommercialStats> findByRegionCodeAndQuarter(String regionCode, Integer quarter);
    List<CommercialStats> findByYearAndQuarter(Integer year, Integer quarter);
    List<CommercialStats> findByRegionCodeOrderByYearAscQuarterAsc(String regionCode);
}
```

- [ ] **Step 2: 실패하는 테스트 작성**

`src/test/java/bigbang/butilkka_be/stats/CommercialStatsQueryServiceTest.java`:

```java
package bigbang.butilkka_be.stats;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CommercialStatsQueryServiceTest {

    @Mock
    private CommercialStatsRepository commercialStatsRepository;

    private CommercialStatsQueryService service;

    @org.junit.jupiter.api.BeforeEach
    void setUp() {
        service = new CommercialStatsQueryService(commercialStatsRepository);
    }

    private static CommercialStats statsOf(String regionCode, int year, int quarter) {
        CommercialStats stats = mock(CommercialStats.class);
        when(stats.getRegionCode()).thenReturn(regionCode);
        when(stats.getYear()).thenReturn(year);
        when(stats.getQuarter()).thenReturn(quarter);
        return stats;
    }

    @Test
    void latestPerRegion_picksMaxYearQuarterPerRegion() {
        CommercialStats a1 = statsOf("A", 2025, 4);
        CommercialStats a2 = statsOf("A", 2026, 1);
        CommercialStats b1 = statsOf("B", 2026, 2);
        when(commercialStatsRepository.findAll()).thenReturn(List.of(a1, a2, b1));

        List<CommercialStats> result = service.latestPerRegion();

        assertThat(result).containsExactlyInAnyOrder(a2, b1);
    }

    @Test
    void latestForRegion_returnsMostRecentRow() {
        CommercialStats older = statsOf("A", 2025, 4);
        CommercialStats newer = statsOf("A", 2026, 1);
        when(commercialStatsRepository.findByRegionCodeOrderByYearAscQuarterAsc("A"))
                .thenReturn(List.of(older, newer));

        Optional<CommercialStats> result = service.latestForRegion("A");

        assertThat(result).contains(newer);
    }

    @Test
    void latestForRegion_withNoData_returnsEmpty() {
        when(commercialStatsRepository.findByRegionCodeOrderByYearAscQuarterAsc("A"))
                .thenReturn(List.of());

        assertThat(service.latestForRegion("A")).isEmpty();
    }

    @Test
    void historyForRegion_delegatesToRepository() {
        List<CommercialStats> expected = List.of(statsOf("A", 2024, 1));
        when(commercialStatsRepository.findByRegionCodeOrderByYearAscQuarterAsc("A")).thenReturn(expected);

        assertThat(service.historyForRegion("A")).isEqualTo(expected);
    }

    @Test
    void parseQuarterLabel_withValidLabel_returnsYearAndQuarter() {
        Optional<int[]> result = CommercialStatsQueryService.parseQuarterLabel("2026Q1");

        assertThat(result).isPresent();
        assertThat(result.get()).containsExactly(2026, 1);
    }

    @Test
    void parseQuarterLabel_withInvalidLabel_returnsEmpty() {
        assertThat(CommercialStatsQueryService.parseQuarterLabel("invalid")).isEmpty();
        assertThat(CommercialStatsQueryService.parseQuarterLabel(null)).isEmpty();
    }
}
```

- [ ] **Step 3: 테스트가 실패하는지 확인**

Run: `./gradlew test --tests "bigbang.butilkka_be.stats.CommercialStatsQueryServiceTest" --console=plain`
Expected: FAIL — `CommercialStatsQueryService` 클래스가 존재하지 않음 (컴파일 에러)

- [ ] **Step 4: `CommercialStatsQueryService` 구현**

`src/main/java/bigbang/butilkka_be/stats/CommercialStatsQueryService.java`:

```java
package bigbang.butilkka_be.stats;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class CommercialStatsQueryService {

    private final CommercialStatsRepository commercialStatsRepository;

    public List<CommercialStats> latestPerRegion() {
        Map<String, CommercialStats> latestByRegion = new LinkedHashMap<>();
        for (CommercialStats stats : commercialStatsRepository.findAll()) {
            latestByRegion.merge(stats.getRegionCode(), stats, CommercialStatsQueryService::laterOf);
        }
        return List.copyOf(latestByRegion.values());
    }

    public Optional<CommercialStats> latestForRegion(String regionCode) {
        List<CommercialStats> history = commercialStatsRepository.findByRegionCodeOrderByYearAscQuarterAsc(regionCode);
        if (history.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(history.get(history.size() - 1));
    }

    public List<CommercialStats> historyForRegion(String regionCode) {
        return commercialStatsRepository.findByRegionCodeOrderByYearAscQuarterAsc(regionCode);
    }

    public List<CommercialStats> forQuarter(int year, int quarter) {
        return commercialStatsRepository.findByYearAndQuarter(year, quarter);
    }

    public static Optional<int[]> parseQuarterLabel(String label) {
        if (label == null || !label.matches("\\d{4}Q[1-4]")) {
            return Optional.empty();
        }
        int year = Integer.parseInt(label.substring(0, 4));
        int quarter = Integer.parseInt(label.substring(5));
        return Optional.of(new int[]{year, quarter});
    }

    private static CommercialStats laterOf(CommercialStats a, CommercialStats b) {
        if (!a.getYear().equals(b.getYear())) {
            return a.getYear() > b.getYear() ? a : b;
        }
        return a.getQuarter() >= b.getQuarter() ? a : b;
    }
}
```

- [ ] **Step 5: 테스트 통과 확인**

Run: `./gradlew test --tests "bigbang.butilkka_be.stats.CommercialStatsQueryServiceTest" --console=plain`
Expected: `BUILD SUCCESSFUL`, 7개 테스트 모두 PASS

- [ ] **Step 6: 커밋**

```bash
git add src/main/java/bigbang/butilkka_be/stats src/test/java/bigbang/butilkka_be/stats/CommercialStatsQueryServiceTest.java
git commit -m "Add CommercialStatsQueryService for latest/history lookups"
```

---

### Task 4: `GET /api/v1/regions/map`

**Files:**
- Create: `src/main/java/bigbang/butilkka_be/region/dto/RegionMapItem.java`
- Create: `src/main/java/bigbang/butilkka_be/region/dto/RegionMapResponse.java`
- Create: `src/main/java/bigbang/butilkka_be/region/RegionMapService.java`
- Create: `src/main/java/bigbang/butilkka_be/region/RegionQueryController.java`
- Test: `src/test/java/bigbang/butilkka_be/region/RegionMapServiceTest.java`
- Test: `src/test/java/bigbang/butilkka_be/region/RegionQueryControllerTest.java`

**Interfaces:**
- Consumes: `CommercialStatsQueryService.latestPerRegion()`, `.forQuarter(int,int)`, `.parseQuarterLabel(String)` (Task 3), `RegionRepository`, `DistrictRepository` (기존)
- Produces: `RegionMapService.getMap(String quarterParam): RegionMapResponse` — 이후 Task 5, 6에서 `RegionQueryController`에 메서드를 추가할 때 이 파일 구조를 따른다.

- [ ] **Step 1: DTO 작성**

`src/main/java/bigbang/butilkka_be/region/dto/RegionMapItem.java`:

```java
package bigbang.butilkka_be.region.dto;

public record RegionMapItem(
        String regionCode,
        String regionName,
        String district,
        String grade
) {}
```

`src/main/java/bigbang/butilkka_be/region/dto/RegionMapResponse.java`:

```java
package bigbang.butilkka_be.region.dto;

import java.util.List;

public record RegionMapResponse(
        String quarter,
        List<RegionMapItem> regions
) {}
```

- [ ] **Step 2: 실패하는 서비스 테스트 작성**

`src/test/java/bigbang/butilkka_be/region/RegionMapServiceTest.java`:

```java
package bigbang.butilkka_be.region;

import bigbang.butilkka_be.common.exception.AppException;
import bigbang.butilkka_be.region.dto.RegionMapResponse;
import bigbang.butilkka_be.stats.CommercialStats;
import bigbang.butilkka_be.stats.CommercialStatsQueryService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RegionMapServiceTest {

    @Mock
    private CommercialStatsQueryService commercialStatsQueryService;
    @Mock
    private RegionRepository regionRepository;
    @Mock
    private DistrictRepository districtRepository;

    private RegionMapService service;

    @org.junit.jupiter.api.BeforeEach
    void setUp() {
        service = new RegionMapService(commercialStatsQueryService, regionRepository, districtRepository);
    }

    @Test
    void getMap_withoutQuarter_usesLatestPerRegionAndResolvesRegionAndDistrict() {
        CommercialStats stats = mock(CommercialStats.class);
        when(stats.getRegionCode()).thenReturn("1168064000");
        when(stats.getYear()).thenReturn(2026);
        when(stats.getQuarter()).thenReturn(4);
        when(stats.getDeclineGrade()).thenReturn("A");
        when(commercialStatsQueryService.latestPerRegion()).thenReturn(List.of(stats));

        Region region = mock(Region.class);
        when(region.getRegionCode()).thenReturn("1168064000");
        when(region.getRegionName()).thenReturn("역삼1동");
        when(region.getDistrictCode()).thenReturn("11680");
        when(regionRepository.findById("1168064000")).thenReturn(java.util.Optional.of(region));

        District district = mock(District.class);
        when(district.getDistrictName()).thenReturn("강남구");
        when(districtRepository.findById("11680")).thenReturn(java.util.Optional.of(district));

        RegionMapResponse response = service.getMap(null);

        assertThat(response.quarter()).isEqualTo("2026Q4");
        assertThat(response.regions()).hasSize(1);
        assertThat(response.regions().get(0).regionCode()).isEqualTo("1168064000");
        assertThat(response.regions().get(0).regionName()).isEqualTo("역삼1동");
        assertThat(response.regions().get(0).district()).isEqualTo("강남구");
        assertThat(response.regions().get(0).grade()).isEqualTo("A");
    }

    @Test
    void getMap_withInvalidQuarter_throwsBadRequest() {
        assertThatThrownBy(() -> service.getMap("invalid"))
                .isInstanceOf(AppException.class)
                .extracting(e -> ((AppException) e).getHttpStatus())
                .isEqualTo(org.springframework.http.HttpStatus.BAD_REQUEST);
    }
}
```

- [ ] **Step 3: 테스트가 실패하는지 확인**

Run: `./gradlew test --tests "bigbang.butilkka_be.region.RegionMapServiceTest" --console=plain`
Expected: FAIL — `RegionMapService` 클래스가 존재하지 않음 (컴파일 에러)

- [ ] **Step 4: `RegionMapService` 구현**

`src/main/java/bigbang/butilkka_be/region/RegionMapService.java`:

```java
package bigbang.butilkka_be.region;

import bigbang.butilkka_be.common.exception.AppException;
import bigbang.butilkka_be.region.dto.RegionMapItem;
import bigbang.butilkka_be.region.dto.RegionMapResponse;
import bigbang.butilkka_be.stats.CommercialStats;
import bigbang.butilkka_be.stats.CommercialStatsQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;

@Service
@RequiredArgsConstructor
public class RegionMapService {

    private final CommercialStatsQueryService commercialStatsQueryService;
    private final RegionRepository regionRepository;
    private final DistrictRepository districtRepository;

    public RegionMapResponse getMap(String quarterParam) {
        List<CommercialStats> statsList;
        String quarterLabel;

        if (quarterParam == null || quarterParam.isBlank()) {
            statsList = commercialStatsQueryService.latestPerRegion();
            quarterLabel = resolveLatestLabel(statsList);
        } else {
            int[] parsed = CommercialStatsQueryService.parseQuarterLabel(quarterParam)
                    .orElseThrow(() -> AppException.badRequest("지원하지 않는 지표입니다."));
            statsList = commercialStatsQueryService.forQuarter(parsed[0], parsed[1]);
            quarterLabel = quarterParam;
        }

        List<RegionMapItem> items = statsList.stream()
                .map(this::toMapItem)
                .toList();

        return new RegionMapResponse(quarterLabel, items);
    }

    private RegionMapItem toMapItem(CommercialStats stats) {
        Region region = regionRepository.findById(stats.getRegionCode())
                .orElseThrow(() -> AppException.notFound("존재하지 않는 상권코드입니다."));
        District district = districtRepository.findById(region.getDistrictCode())
                .orElseThrow(() -> AppException.notFound("존재하지 않는 자치구코드입니다."));
        return new RegionMapItem(region.getRegionCode(), region.getRegionName(), district.getDistrictName(), stats.getDeclineGrade());
    }

    private String resolveLatestLabel(List<CommercialStats> statsList) {
        return statsList.stream()
                .max(Comparator.comparingInt(CommercialStats::getYear).thenComparingInt(CommercialStats::getQuarter))
                .map(s -> s.getYear() + "Q" + s.getQuarter())
                .orElse(null);
    }
}
```

- [ ] **Step 5: 서비스 테스트 통과 확인**

Run: `./gradlew test --tests "bigbang.butilkka_be.region.RegionMapServiceTest" --console=plain`
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 6: 실패하는 컨트롤러 테스트 작성**

`src/test/java/bigbang/butilkka_be/region/RegionQueryControllerTest.java`:

```java
package bigbang.butilkka_be.region;

import bigbang.butilkka_be.common.security.JwtTokenProvider;
import bigbang.butilkka_be.region.dto.RegionMapItem;
import bigbang.butilkka_be.region.dto.RegionMapResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(RegionQueryController.class)
@AutoConfigureMockMvc(addFilters = false)
class RegionQueryControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private RegionMapService regionMapService;

    @MockitoBean
    private JwtTokenProvider jwtTokenProvider;

    @Test
    void getMap_returnsOk() throws Exception {
        when(regionMapService.getMap(isNull()))
                .thenReturn(new RegionMapResponse("2026Q4", List.of(
                        new RegionMapItem("1168064000", "역삼1동", "강남구", "A"))));

        mockMvc.perform(get("/api/v1/regions/map"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.regions[0].regionName").value("역삼1동"));
    }
}
```

- [ ] **Step 7: 테스트가 실패하는지 확인**

Run: `./gradlew test --tests "bigbang.butilkka_be.region.RegionQueryControllerTest" --console=plain`
Expected: FAIL — `RegionQueryController` 클래스가 존재하지 않음 (컴파일 에러)

- [ ] **Step 8: `RegionQueryController` 구현**

`src/main/java/bigbang/butilkka_be/region/RegionQueryController.java`:

```java
package bigbang.butilkka_be.region;

import bigbang.butilkka_be.common.response.ApiResponse;
import bigbang.butilkka_be.region.dto.RegionMapResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/regions")
@RequiredArgsConstructor
public class RegionQueryController {

    private final RegionMapService regionMapService;

    @GetMapping("/map")
    public ResponseEntity<ApiResponse<RegionMapResponse>> getMap(
            @RequestParam(required = false) String quarter) {
        RegionMapResponse response = regionMapService.getMap(quarter);
        return ResponseEntity.ok(ApiResponse.ok("지도 데이터 조회 성공", response));
    }
}
```

- [ ] **Step 9: 테스트 통과 확인**

Run: `./gradlew test --tests "bigbang.butilkka_be.region.RegionMapServiceTest" --tests "bigbang.butilkka_be.region.RegionQueryControllerTest" --console=plain`
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 10: 커밋**

```bash
git add src/main/java/bigbang/butilkka_be/region src/test/java/bigbang/butilkka_be/region
git commit -m "Add GET /api/v1/regions/map endpoint"
```

---

### Task 5: `GET /api/v1/regions/declineRanking`

**Files:**
- Create: `src/main/java/bigbang/butilkka_be/region/dto/RegionRankingItem.java`
- Create: `src/main/java/bigbang/butilkka_be/region/dto/RegionRankingResponse.java`
- Create: `src/main/java/bigbang/butilkka_be/region/RegionRankingService.java`
- Modify: `src/main/java/bigbang/butilkka_be/region/RegionQueryController.java` (Task 4에서 생성)
- Test: `src/test/java/bigbang/butilkka_be/region/RegionRankingServiceTest.java`
- Test: Modify `src/test/java/bigbang/butilkka_be/region/RegionQueryControllerTest.java` (Task 4에서 생성)

**Interfaces:**
- Consumes: `CommercialStatsQueryService.latestPerRegion()`, `.historyForRegion(String)`, `.forQuarter(int,int)`, `.parseQuarterLabel(String)` (Task 3)
- Produces: `RegionRankingService.getRanking(String order, String quarterParam): RegionRankingResponse`

- [ ] **Step 1: DTO 작성**

`src/main/java/bigbang/butilkka_be/region/dto/RegionRankingItem.java`:

```java
package bigbang.butilkka_be.region.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record RegionRankingItem(
        int rank,
        String regionCode,
        String regionName,
        @JsonProperty("decline_grade") String declineGrade,
        String direction
) {}
```

`src/main/java/bigbang/butilkka_be/region/dto/RegionRankingResponse.java`:

```java
package bigbang.butilkka_be.region.dto;

import java.util.List;

public record RegionRankingResponse(
        String order,
        String quarter,
        List<RegionRankingItem> regions
) {}
```

- [ ] **Step 2: 실패하는 서비스 테스트 작성**

`src/test/java/bigbang/butilkka_be/region/RegionRankingServiceTest.java`:

```java
package bigbang.butilkka_be.region;

import bigbang.butilkka_be.common.exception.AppException;
import bigbang.butilkka_be.region.dto.RegionRankingResponse;
import bigbang.butilkka_be.stats.CommercialStats;
import bigbang.butilkka_be.stats.CommercialStatsQueryService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RegionRankingServiceTest {

    @Mock
    private CommercialStatsQueryService commercialStatsQueryService;
    @Mock
    private RegionRepository regionRepository;

    private RegionRankingService service;

    @org.junit.jupiter.api.BeforeEach
    void setUp() {
        service = new RegionRankingService(commercialStatsQueryService, regionRepository);
    }

    private static CommercialStats statsOf(String regionCode, String grade, int year, int quarter) {
        CommercialStats stats = mock(CommercialStats.class);
        when(stats.getRegionCode()).thenReturn(regionCode);
        when(stats.getDeclineGrade()).thenReturn(grade);
        when(stats.getYear()).thenReturn(year);
        when(stats.getQuarter()).thenReturn(quarter);
        return stats;
    }

    @Test
    void getRanking_withOrderTop_sortsBestGradeFirst() {
        CommercialStats a = statsOf("A", "A", 2026, 4);
        CommercialStats b = statsOf("B", "E", 2026, 4);
        when(commercialStatsQueryService.latestPerRegion()).thenReturn(List.of(b, a));
        when(commercialStatsQueryService.historyForRegion("A")).thenReturn(List.of(a));
        when(commercialStatsQueryService.historyForRegion("B")).thenReturn(List.of(b));

        Region regionA = mock(Region.class);
        when(regionA.getRegionName()).thenReturn("A동");
        when(regionRepository.findById("A")).thenReturn(java.util.Optional.of(regionA));
        Region regionB = mock(Region.class);
        when(regionB.getRegionName()).thenReturn("B동");
        when(regionRepository.findById("B")).thenReturn(java.util.Optional.of(regionB));

        RegionRankingResponse response = service.getRanking("top", null);

        assertThat(response.regions().get(0).regionCode()).isEqualTo("A");
        assertThat(response.regions().get(0).rank()).isEqualTo(1);
        assertThat(response.regions().get(0).direction()).isEqualTo("FLAT");
    }

    @Test
    void getRanking_withInvalidOrder_throwsBadRequest() {
        assertThatThrownBy(() -> service.getRanking("sideways", null))
                .isInstanceOf(AppException.class)
                .extracting(e -> ((AppException) e).getHttpStatus())
                .isEqualTo(org.springframework.http.HttpStatus.BAD_REQUEST);
    }
}
```

- [ ] **Step 3: 테스트가 실패하는지 확인**

Run: `./gradlew test --tests "bigbang.butilkka_be.region.RegionRankingServiceTest" --console=plain`
Expected: FAIL — `RegionRankingService` 클래스가 존재하지 않음 (컴파일 에러)

- [ ] **Step 4: `RegionRankingService` 구현**

`src/main/java/bigbang/butilkka_be/region/RegionRankingService.java`:

```java
package bigbang.butilkka_be.region;

import bigbang.butilkka_be.common.exception.AppException;
import bigbang.butilkka_be.region.dto.RegionRankingItem;
import bigbang.butilkka_be.region.dto.RegionRankingResponse;
import bigbang.butilkka_be.stats.CommercialStats;
import bigbang.butilkka_be.stats.CommercialStatsQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;

@Service
@RequiredArgsConstructor
public class RegionRankingService {

    private static final String GRADE_ORDER = "ABCDE";

    private final CommercialStatsQueryService commercialStatsQueryService;
    private final RegionRepository regionRepository;

    public RegionRankingResponse getRanking(String order, String quarterParam) {
        if (!"top".equals(order) && !"bottom".equals(order)) {
            throw AppException.badRequest("지원하지 않는 지표 또는 정렬 기준입니다.");
        }

        List<CommercialStats> statsList;
        String quarterLabel;
        if (quarterParam == null || quarterParam.isBlank()) {
            statsList = commercialStatsQueryService.latestPerRegion();
            quarterLabel = resolveLatestLabel(statsList);
        } else {
            int[] parsed = CommercialStatsQueryService.parseQuarterLabel(quarterParam)
                    .orElseThrow(() -> AppException.badRequest("지원하지 않는 지표 또는 정렬 기준입니다."));
            statsList = commercialStatsQueryService.forQuarter(parsed[0], parsed[1]);
            quarterLabel = quarterParam;
        }

        Comparator<CommercialStats> byGrade = Comparator.comparingInt(s -> GRADE_ORDER.indexOf(s.getDeclineGrade()));
        if ("bottom".equals(order)) {
            byGrade = byGrade.reversed();
        }

        List<CommercialStats> sorted = statsList.stream().sorted(byGrade).limit(5).toList();

        List<RegionRankingItem> items = new java.util.ArrayList<>();
        for (int i = 0; i < sorted.size(); i++) {
            items.add(toRankingItem(sorted.get(i), i + 1));
        }

        return new RegionRankingResponse(order, quarterLabel, items);
    }

    private RegionRankingItem toRankingItem(CommercialStats stats, int rank) {
        Region region = regionRepository.findById(stats.getRegionCode())
                .orElseThrow(() -> AppException.notFound("존재하지 않는 상권코드입니다."));
        String direction = resolveDirection(stats);
        return new RegionRankingItem(rank, stats.getRegionCode(), region.getRegionName(), stats.getDeclineGrade(), direction);
    }

    private String resolveDirection(CommercialStats current) {
        List<CommercialStats> history = commercialStatsQueryService.historyForRegion(current.getRegionCode());
        int currentIndex = history.indexOf(current);
        if (currentIndex <= 0) {
            return "FLAT";
        }
        CommercialStats previous = history.get(currentIndex - 1);
        int currentRank = GRADE_ORDER.indexOf(current.getDeclineGrade());
        int previousRank = GRADE_ORDER.indexOf(previous.getDeclineGrade());
        if (currentRank < previousRank) {
            return "UP";
        }
        if (currentRank > previousRank) {
            return "DOWN";
        }
        return "FLAT";
    }

    private String resolveLatestLabel(List<CommercialStats> statsList) {
        return statsList.stream()
                .max(Comparator.comparingInt(CommercialStats::getYear).thenComparingInt(CommercialStats::getQuarter))
                .map(s -> s.getYear() + "Q" + s.getQuarter())
                .orElse(null);
    }
}
```

- [ ] **Step 5: 서비스 테스트 통과 확인**

Run: `./gradlew test --tests "bigbang.butilkka_be.region.RegionRankingServiceTest" --console=plain`
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 6: 컨트롤러 테스트에 랭킹 케이스 추가 (실패 확인)**

`src/test/java/bigbang/butilkka_be/region/RegionQueryControllerTest.java`의 `@MockitoBean private RegionMapService regionMapService;` 바로 뒤에 필드 추가:

```java
    @MockitoBean
    private RegionRankingService regionRankingService;
```

같은 파일의 마지막 `@Test` 메서드 뒤(클래스 닫는 `}` 앞)에 테스트 추가:

```java
    @Test
    void getRanking_returnsOk() throws Exception {
        when(regionRankingService.getRanking("top", null))
                .thenReturn(new bigbang.butilkka_be.region.dto.RegionRankingResponse("top", "2026Q4", List.of(
                        new bigbang.butilkka_be.region.dto.RegionRankingItem(1, "1168064000", "역삼1동", "A", "UP"))));

        mockMvc.perform(get("/api/v1/regions/declineRanking").param("order", "top"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.regions[0].regionName").value("역삼1동"));
    }
```

Run: `./gradlew test --tests "bigbang.butilkka_be.region.RegionQueryControllerTest" --console=plain`
Expected: FAIL — `RegionQueryController`에 `/declineRanking` 매핑이 없어 404 (테스트에서 200 기대와 불일치)

- [ ] **Step 7: `RegionQueryController`에 엔드포인트 추가**

`src/main/java/bigbang/butilkka_be/region/RegionQueryController.java` 전체를 아래로 교체:

```java
package bigbang.butilkka_be.region;

import bigbang.butilkka_be.common.response.ApiResponse;
import bigbang.butilkka_be.region.dto.RegionMapResponse;
import bigbang.butilkka_be.region.dto.RegionRankingResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/regions")
@RequiredArgsConstructor
public class RegionQueryController {

    private final RegionMapService regionMapService;
    private final RegionRankingService regionRankingService;

    @GetMapping("/map")
    public ResponseEntity<ApiResponse<RegionMapResponse>> getMap(
            @RequestParam(required = false) String quarter) {
        RegionMapResponse response = regionMapService.getMap(quarter);
        return ResponseEntity.ok(ApiResponse.ok("지도 데이터 조회 성공", response));
    }

    @GetMapping("/declineRanking")
    public ResponseEntity<ApiResponse<RegionRankingResponse>> getDeclineRanking(
            @RequestParam String order,
            @RequestParam(required = false) String quarter) {
        RegionRankingResponse response = regionRankingService.getRanking(order, quarter);
        return ResponseEntity.ok(ApiResponse.ok("순위 조회 성공", response));
    }
}
```

- [ ] **Step 8: 테스트 통과 확인**

Run: `./gradlew test --tests "bigbang.butilkka_be.region.*" --console=plain`
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 9: 커밋**

```bash
git add src/main/java/bigbang/butilkka_be/region src/test/java/bigbang/butilkka_be/region
git commit -m "Add GET /api/v1/regions/declineRanking endpoint"
```

---

### Task 6: `GET /api/v1/regions/search`

**Files:**
- Modify: `src/main/java/bigbang/butilkka_be/region/RegionRepository.java`
- Create: `src/main/java/bigbang/butilkka_be/region/dto/RegionSearchItem.java`
- Create: `src/main/java/bigbang/butilkka_be/region/RegionSearchService.java`
- Modify: `src/main/java/bigbang/butilkka_be/region/RegionQueryController.java`
- Test: `src/test/java/bigbang/butilkka_be/region/RegionSearchServiceTest.java`
- Test: Modify `src/test/java/bigbang/butilkka_be/region/RegionQueryControllerTest.java`

**Interfaces:**
- Produces: `RegionSearchService.search(String keyword): List<RegionSearchItem>`

- [ ] **Step 1: Repository에 검색 메서드 추가**

`src/main/java/bigbang/butilkka_be/region/RegionRepository.java` 전체를 아래로 교체:

```java
package bigbang.butilkka_be.region;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface RegionRepository extends JpaRepository<Region, String> {
    List<Region> findByRegionNameContaining(String keyword);
}
```

- [ ] **Step 2: DTO 작성**

`src/main/java/bigbang/butilkka_be/region/dto/RegionSearchItem.java`:

```java
package bigbang.butilkka_be.region.dto;

public record RegionSearchItem(
        String regionCode,
        String regionName,
        String district
) {}
```

- [ ] **Step 3: 실패하는 서비스 테스트 작성**

`src/test/java/bigbang/butilkka_be/region/RegionSearchServiceTest.java`:

```java
package bigbang.butilkka_be.region;

import bigbang.butilkka_be.common.exception.AppException;
import bigbang.butilkka_be.region.dto.RegionSearchItem;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RegionSearchServiceTest {

    @Mock
    private RegionRepository regionRepository;
    @Mock
    private DistrictRepository districtRepository;

    private RegionSearchService service;

    @org.junit.jupiter.api.BeforeEach
    void setUp() {
        service = new RegionSearchService(regionRepository, districtRepository);
    }

    @Test
    void search_withMatchingKeyword_returnsResultsWithDistrict() {
        Region region = mock(Region.class);
        when(region.getRegionCode()).thenReturn("1168064000");
        when(region.getRegionName()).thenReturn("역삼1동");
        when(region.getDistrictCode()).thenReturn("11680");
        when(regionRepository.findByRegionNameContaining("역삼")).thenReturn(List.of(region));

        District district = mock(District.class);
        when(district.getDistrictName()).thenReturn("강남구");
        when(districtRepository.findById("11680")).thenReturn(Optional.of(district));

        List<RegionSearchItem> result = service.search("역삼");

        assertThat(result).hasSize(1);
        assertThat(result.get(0).regionName()).isEqualTo("역삼1동");
        assertThat(result.get(0).district()).isEqualTo("강남구");
    }

    @Test
    void search_withBlankKeyword_throwsBadRequest() {
        assertThatThrownBy(() -> service.search(" "))
                .isInstanceOf(AppException.class)
                .extracting(e -> ((AppException) e).getHttpStatus())
                .isEqualTo(org.springframework.http.HttpStatus.BAD_REQUEST);
    }
}
```

- [ ] **Step 4: 테스트가 실패하는지 확인**

Run: `./gradlew test --tests "bigbang.butilkka_be.region.RegionSearchServiceTest" --console=plain`
Expected: FAIL — `RegionSearchService` 클래스가 존재하지 않음 (컴파일 에러)

- [ ] **Step 5: `RegionSearchService` 구현**

`src/main/java/bigbang/butilkka_be/region/RegionSearchService.java`:

```java
package bigbang.butilkka_be.region;

import bigbang.butilkka_be.common.exception.AppException;
import bigbang.butilkka_be.region.dto.RegionSearchItem;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class RegionSearchService {

    private final RegionRepository regionRepository;
    private final DistrictRepository districtRepository;

    public List<RegionSearchItem> search(String keyword) {
        if (keyword == null || keyword.isBlank()) {
            throw AppException.badRequest("검색어를 입력해주세요.");
        }

        return regionRepository.findByRegionNameContaining(keyword).stream()
                .map(this::toSearchItem)
                .toList();
    }

    private RegionSearchItem toSearchItem(Region region) {
        District district = districtRepository.findById(region.getDistrictCode())
                .orElseThrow(() -> AppException.notFound("존재하지 않는 자치구코드입니다."));
        return new RegionSearchItem(region.getRegionCode(), region.getRegionName(), district.getDistrictName());
    }
}
```

- [ ] **Step 6: 서비스 테스트 통과 확인**

Run: `./gradlew test --tests "bigbang.butilkka_be.region.RegionSearchServiceTest" --console=plain`
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 7: 컨트롤러 테스트에 검색 케이스 추가 (실패 확인)**

`src/test/java/bigbang/butilkka_be/region/RegionQueryControllerTest.java`의 `@MockitoBean private RegionRankingService regionRankingService;` 바로 뒤에 필드 추가:

```java
    @MockitoBean
    private RegionSearchService regionSearchService;
```

마지막 `@Test` 메서드 뒤에 테스트 추가:

```java
    @Test
    void search_returnsOk() throws Exception {
        when(regionSearchService.search("역삼"))
                .thenReturn(List.of(new bigbang.butilkka_be.region.dto.RegionSearchItem("1168064000", "역삼1동", "강남구")));

        mockMvc.perform(get("/api/v1/regions/search").param("keyword", "역삼"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].regionName").value("역삼1동"));
    }
```

Run: `./gradlew test --tests "bigbang.butilkka_be.region.RegionQueryControllerTest" --console=plain`
Expected: FAIL — `RegionQueryController`에 `/search` 매핑이 없어 404

- [ ] **Step 8: `RegionQueryController`에 엔드포인트 추가**

`src/main/java/bigbang/butilkka_be/region/RegionQueryController.java` 전체를 아래로 교체:

```java
package bigbang.butilkka_be.region;

import bigbang.butilkka_be.common.response.ApiResponse;
import bigbang.butilkka_be.region.dto.RegionMapResponse;
import bigbang.butilkka_be.region.dto.RegionRankingResponse;
import bigbang.butilkka_be.region.dto.RegionSearchItem;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/regions")
@RequiredArgsConstructor
public class RegionQueryController {

    private final RegionMapService regionMapService;
    private final RegionRankingService regionRankingService;
    private final RegionSearchService regionSearchService;

    @GetMapping("/map")
    public ResponseEntity<ApiResponse<RegionMapResponse>> getMap(
            @RequestParam(required = false) String quarter) {
        RegionMapResponse response = regionMapService.getMap(quarter);
        return ResponseEntity.ok(ApiResponse.ok("지도 데이터 조회 성공", response));
    }

    @GetMapping("/declineRanking")
    public ResponseEntity<ApiResponse<RegionRankingResponse>> getDeclineRanking(
            @RequestParam String order,
            @RequestParam(required = false) String quarter) {
        RegionRankingResponse response = regionRankingService.getRanking(order, quarter);
        return ResponseEntity.ok(ApiResponse.ok("순위 조회 성공", response));
    }

    @GetMapping("/search")
    public ResponseEntity<ApiResponse<List<RegionSearchItem>>> search(
            @RequestParam String keyword) {
        List<RegionSearchItem> results = regionSearchService.search(keyword);
        return ResponseEntity.ok(ApiResponse.ok("상권 검색 성공", results));
    }
}
```

- [ ] **Step 9: 테스트 통과 확인**

Run: `./gradlew test --tests "bigbang.butilkka_be.region.*" --console=plain`
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 10: 커밋**

```bash
git add src/main/java/bigbang/butilkka_be/region src/test/java/bigbang/butilkka_be/region
git commit -m "Add GET /api/v1/regions/search endpoint"
```

---

### Task 7: `GET /api/v1/districts/{districtsCode}` — 상권 상세

**Files:**
- Create: `src/main/java/bigbang/butilkka_be/region/dto/MetricTrendPoint.java`
- Create: `src/main/java/bigbang/butilkka_be/region/dto/MetricSummary.java`
- Create: `src/main/java/bigbang/butilkka_be/region/dto/ClosureRateSummary.java`
- Create: `src/main/java/bigbang/butilkka_be/region/dto/CategoryCount.java`
- Create: `src/main/java/bigbang/butilkka_be/region/dto/StoreCountSummary.java`
- Create: `src/main/java/bigbang/butilkka_be/region/dto/RegionDetailResponse.java`
- Create: `src/main/java/bigbang/butilkka_be/region/RegionDetailService.java`
- Create: `src/main/java/bigbang/butilkka_be/region/RegionDetailController.java`
- Test: `src/test/java/bigbang/butilkka_be/region/RegionDetailServiceTest.java`
- Test: `src/test/java/bigbang/butilkka_be/region/RegionDetailControllerTest.java`

**Interfaces:**
- Consumes: `CommercialStatsQueryService.historyForRegion(String)` (Task 3), `CategoryRepository` (기존, `findById`)
- Produces: `RegionDetailService.getDetail(String regionCode): RegionDetailResponse`

- [ ] **Step 1: DTO 작성**

`src/main/java/bigbang/butilkka_be/region/dto/MetricTrendPoint.java`:

```java
package bigbang.butilkka_be.region.dto;

public record MetricTrendPoint(String quarter, Number value) {}
```

`src/main/java/bigbang/butilkka_be/region/dto/MetricSummary.java`:

```java
package bigbang.butilkka_be.region.dto;

import java.util.List;

public record MetricSummary(
        Number value,
        Number changeRate,
        String direction,
        List<MetricTrendPoint> trend
) {}
```

`src/main/java/bigbang/butilkka_be/region/dto/ClosureRateSummary.java`:

```java
package bigbang.butilkka_be.region.dto;

import java.util.List;

public record ClosureRateSummary(
        Number value,
        Number changeRate,
        String direction,
        List<MetricTrendPoint> trend,
        Number avgOperatingYears,
        Number seoulAvgOperatingYears
) {}
```

`src/main/java/bigbang/butilkka_be/region/dto/CategoryCount.java`:

```java
package bigbang.butilkka_be.region.dto;

public record CategoryCount(String category, int count) {}
```

`src/main/java/bigbang/butilkka_be/region/dto/StoreCountSummary.java`:

```java
package bigbang.butilkka_be.region.dto;

import java.util.List;

public record StoreCountSummary(
        Number value,
        Number changeCount,
        String direction,
        List<MetricTrendPoint> trend,
        List<CategoryCount> categoryDistribution
) {}
```

`src/main/java/bigbang/butilkka_be/region/dto/RegionDetailResponse.java`:

```java
package bigbang.butilkka_be.region.dto;

public record RegionDetailResponse(
        String regionCode,
        String district,
        String regionName,
        String quarter,
        DeclineGradeSummary declineGrade,
        MetricSummary rentRatio,
        MetricSummary footTraffic,
        MetricSummary vacancyRate,
        ClosureRateSummary closureRate,
        StoreCountSummary storeCount
) {
    public record DeclineGradeSummary(String current, String previous, java.util.List<GradeTrendPoint> trend) {}

    public record GradeTrendPoint(String quarter, String grade) {}
}
```

- [ ] **Step 2: 실패하는 서비스 테스트 작성**

`src/test/java/bigbang/butilkka_be/region/RegionDetailServiceTest.java`:

```java
package bigbang.butilkka_be.region;

import bigbang.butilkka_be.category.Category;
import bigbang.butilkka_be.category.CategoryRepository;
import bigbang.butilkka_be.common.exception.AppException;
import bigbang.butilkka_be.region.dto.RegionDetailResponse;
import bigbang.butilkka_be.stats.CommercialStats;
import bigbang.butilkka_be.stats.CommercialStatsQueryService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RegionDetailServiceTest {

    @Mock
    private CommercialStatsQueryService commercialStatsQueryService;
    @Mock
    private RegionRepository regionRepository;
    @Mock
    private DistrictRepository districtRepository;
    @Mock
    private CategoryRepository categoryRepository;

    private RegionDetailService service;

    @org.junit.jupiter.api.BeforeEach
    void setUp() {
        service = new RegionDetailService(commercialStatsQueryService, regionRepository, districtRepository, categoryRepository);
    }

    private static CommercialStats statsOf(int year, int quarter, String grade, int footTraffic, long rentAmount,
                                            BigDecimal vacancyRate, BigDecimal closureRate, BigDecimal avgBusinessPeriod,
                                            int storeCount, String categoryCode) {
        CommercialStats stats = mock(CommercialStats.class);
        when(stats.getYear()).thenReturn(year);
        when(stats.getQuarter()).thenReturn(quarter);
        when(stats.getDeclineGrade()).thenReturn(grade);
        when(stats.getFootTraffic()).thenReturn(footTraffic);
        when(stats.getFootTrafficDelta()).thenReturn(BigDecimal.ZERO);
        when(stats.getRentAmount()).thenReturn(rentAmount);
        when(stats.getRentDelta()).thenReturn(BigDecimal.ZERO);
        when(stats.getVacancyRate()).thenReturn(vacancyRate);
        when(stats.getVacancyRateDelta()).thenReturn(BigDecimal.ZERO);
        when(stats.getClosureRate()).thenReturn(closureRate);
        when(stats.getClosureRateDelta()).thenReturn(BigDecimal.ZERO);
        when(stats.getAvgBusinessPeriod()).thenReturn(avgBusinessPeriod);
        when(stats.getStoreCount()).thenReturn(storeCount);
        when(stats.getStoreCountDelta()).thenReturn(BigDecimal.ZERO);
        when(stats.getCategoryCode()).thenReturn(categoryCode);
        when(stats.getRegionCode()).thenReturn("1168064000");
        return stats;
    }

    @Test
    void getDetail_withHistory_buildsAllMetricSummaries() {
        CommercialStats q1 = statsOf(2026, 1, "B", 100000, 40000000L, new BigDecimal("4.0"), new BigDecimal("5.0"), new BigDecimal("3.0"), 450, "CS100001");
        CommercialStats q2 = statsOf(2026, 2, "A", 110000, 41000000L, new BigDecimal("3.5"), new BigDecimal("4.5"), new BigDecimal("3.1"), 452, "CS100001");
        when(commercialStatsQueryService.historyForRegion("1168064000")).thenReturn(List.of(q1, q2));

        Region region = mock(Region.class);
        when(region.getRegionCode()).thenReturn("1168064000");
        when(region.getRegionName()).thenReturn("역삼1동");
        when(region.getDistrictCode()).thenReturn("11680");
        when(regionRepository.findById("1168064000")).thenReturn(Optional.of(region));

        District district = mock(District.class);
        when(district.getDistrictName()).thenReturn("강남구");
        when(districtRepository.findById("11680")).thenReturn(Optional.of(district));

        Category category = mock(Category.class);
        when(category.getCategoryName()).thenReturn("한식음식점");
        when(categoryRepository.findById("CS100001")).thenReturn(Optional.of(category));

        RegionDetailResponse response = service.getDetail("1168064000");

        assertThat(response.regionName()).isEqualTo("역삼1동");
        assertThat(response.declineGrade().current()).isEqualTo("A");
        assertThat(response.declineGrade().previous()).isEqualTo("B");
        assertThat(response.declineGrade().trend()).hasSize(2);
        assertThat(response.footTraffic().value()).isEqualTo(110000);
        assertThat(response.storeCount().categoryDistribution()).hasSize(1);
        assertThat(response.storeCount().categoryDistribution().get(0).category()).isEqualTo("한식음식점");
    }

    @Test
    void getDetail_withUnknownRegion_throwsNotFound() {
        when(regionRepository.findById("UNKNOWN")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getDetail("UNKNOWN"))
                .isInstanceOf(AppException.class)
                .extracting(e -> ((AppException) e).getHttpStatus())
                .isEqualTo(org.springframework.http.HttpStatus.NOT_FOUND);
    }
}
```

- [ ] **Step 3: 테스트가 실패하는지 확인**

Run: `./gradlew test --tests "bigbang.butilkka_be.region.RegionDetailServiceTest" --console=plain`
Expected: FAIL — `RegionDetailService` 클래스가 존재하지 않음 (컴파일 에러)

- [ ] **Step 4: `RegionDetailService` 구현**

`src/main/java/bigbang/butilkka_be/region/RegionDetailService.java`:

```java
package bigbang.butilkka_be.region;

import bigbang.butilkka_be.category.Category;
import bigbang.butilkka_be.category.CategoryRepository;
import bigbang.butilkka_be.common.exception.AppException;
import bigbang.butilkka_be.region.dto.CategoryCount;
import bigbang.butilkka_be.region.dto.ClosureRateSummary;
import bigbang.butilkka_be.region.dto.MetricSummary;
import bigbang.butilkka_be.region.dto.MetricTrendPoint;
import bigbang.butilkka_be.region.dto.RegionDetailResponse;
import bigbang.butilkka_be.region.dto.StoreCountSummary;
import bigbang.butilkka_be.stats.CommercialStats;
import bigbang.butilkka_be.stats.CommercialStatsQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;

@Service
@RequiredArgsConstructor
public class RegionDetailService {

    private static final BigDecimal SEOUL_AVG_OPERATING_YEARS = new BigDecimal("4.1");

    private final CommercialStatsQueryService commercialStatsQueryService;
    private final RegionRepository regionRepository;
    private final DistrictRepository districtRepository;
    private final CategoryRepository categoryRepository;

    public RegionDetailResponse getDetail(String regionCode) {
        Region region = regionRepository.findById(regionCode)
                .orElseThrow(() -> AppException.notFound("존재하지 않는 상권코드입니다."));
        District district = districtRepository.findById(region.getDistrictCode())
                .orElseThrow(() -> AppException.notFound("존재하지 않는 자치구코드입니다."));

        List<CommercialStats> history = commercialStatsQueryService.historyForRegion(regionCode);
        if (history.isEmpty()) {
            throw AppException.notFound("존재하지 않는 상권코드입니다.");
        }

        CommercialStats latest = history.get(history.size() - 1);
        CommercialStats previous = history.size() >= 2 ? history.get(history.size() - 2) : null;

        return new RegionDetailResponse(
                region.getRegionCode(),
                district.getDistrictName(),
                region.getRegionName(),
                label(latest),
                buildGradeSummary(history, latest, previous),
                buildMetricSummary(history, CommercialStats::getRentAmount, CommercialStats::getRentDelta),
                buildMetricSummary(history, s -> (Number) s.getFootTraffic(), CommercialStats::getFootTrafficDelta),
                buildMetricSummary(history, CommercialStats::getVacancyRate, CommercialStats::getVacancyRateDelta),
                buildClosureRateSummary(history, latest),
                buildStoreCountSummary(history, latest)
        );
    }

    private RegionDetailResponse.DeclineGradeSummary buildGradeSummary(
            List<CommercialStats> history, CommercialStats latest, CommercialStats previous) {
        List<RegionDetailResponse.GradeTrendPoint> trend = history.stream()
                .map(s -> new RegionDetailResponse.GradeTrendPoint(label(s), s.getDeclineGrade()))
                .toList();
        String previousGrade = previous != null ? previous.getDeclineGrade() : null;
        return new RegionDetailResponse.DeclineGradeSummary(latest.getDeclineGrade(), previousGrade, trend);
    }

    private MetricSummary buildMetricSummary(
            List<CommercialStats> history,
            java.util.function.Function<CommercialStats, Number> valueFn,
            java.util.function.Function<CommercialStats, Number> deltaFn) {
        CommercialStats latest = history.get(history.size() - 1);
        List<MetricTrendPoint> trend = history.stream()
                .map(s -> new MetricTrendPoint(label(s), valueFn.apply(s)))
                .toList();
        String direction = resolveDirection(deltaFn.apply(latest));
        return new MetricSummary(valueFn.apply(latest), deltaFn.apply(latest), direction, trend);
    }

    private ClosureRateSummary buildClosureRateSummary(List<CommercialStats> history, CommercialStats latest) {
        List<MetricTrendPoint> trend = history.stream()
                .map(s -> new MetricTrendPoint(label(s), s.getClosureRate()))
                .toList();
        String direction = resolveDirection(latest.getClosureRateDelta());
        return new ClosureRateSummary(
                latest.getClosureRate(), latest.getClosureRateDelta(), direction, trend,
                latest.getAvgBusinessPeriod(), SEOUL_AVG_OPERATING_YEARS);
    }

    private StoreCountSummary buildStoreCountSummary(List<CommercialStats> history, CommercialStats latest) {
        List<MetricTrendPoint> trend = history.stream()
                .map(s -> new MetricTrendPoint(label(s), s.getStoreCount()))
                .toList();
        String direction = resolveDirection(latest.getStoreCountDelta());
        Category category = categoryRepository.findById(latest.getCategoryCode())
                .orElseThrow(() -> AppException.notFound("존재하지 않는 업종코드입니다."));
        List<CategoryCount> categoryDistribution = List.of(
                new CategoryCount(category.getCategoryName(), latest.getStoreCount()));
        return new StoreCountSummary(latest.getStoreCount(), latest.getStoreCountDelta(), direction, trend, categoryDistribution);
    }

    private String resolveDirection(Number delta) {
        double value = delta.doubleValue();
        if (value > 0) {
            return "UP";
        }
        if (value < 0) {
            return "DOWN";
        }
        return "FLAT";
    }

    private String label(CommercialStats stats) {
        return stats.getYear() + "Q" + stats.getQuarter();
    }
}
```

- [ ] **Step 5: 서비스 테스트 통과 확인**

Run: `./gradlew test --tests "bigbang.butilkka_be.region.RegionDetailServiceTest" --console=plain`
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 6: 실패하는 컨트롤러 테스트 작성**

`src/test/java/bigbang/butilkka_be/region/RegionDetailControllerTest.java`:

```java
package bigbang.butilkka_be.region;

import bigbang.butilkka_be.common.security.JwtTokenProvider;
import bigbang.butilkka_be.region.dto.RegionDetailResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(RegionDetailController.class)
@AutoConfigureMockMvc(addFilters = false)
class RegionDetailControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private RegionDetailService regionDetailService;

    @MockitoBean
    private JwtTokenProvider jwtTokenProvider;

    @Test
    void getDetail_returnsOk() throws Exception {
        when(regionDetailService.getDetail("1168064000")).thenReturn(new RegionDetailResponse(
                "1168064000", "강남구", "역삼1동", "2026Q4",
                new RegionDetailResponse.DeclineGradeSummary("A", "C", List.of()),
                null, null, null, null, null));

        mockMvc.perform(get("/api/v1/districts/1168064000"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.regionName").value("역삼1동"));
    }
}
```

- [ ] **Step 7: 테스트가 실패하는지 확인**

Run: `./gradlew test --tests "bigbang.butilkka_be.region.RegionDetailControllerTest" --console=plain`
Expected: FAIL — `RegionDetailController` 클래스가 존재하지 않음 (컴파일 에러)

- [ ] **Step 8: `RegionDetailController` 구현**

`src/main/java/bigbang/butilkka_be/region/RegionDetailController.java`:

```java
package bigbang.butilkka_be.region;

import bigbang.butilkka_be.common.response.ApiResponse;
import bigbang.butilkka_be.region.dto.RegionDetailResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/districts")
@RequiredArgsConstructor
public class RegionDetailController {

    private final RegionDetailService regionDetailService;

    @GetMapping("/{districtsCode}")
    public ResponseEntity<ApiResponse<RegionDetailResponse>> getDetail(
            @PathVariable("districtsCode") String districtsCode) {
        RegionDetailResponse response = regionDetailService.getDetail(districtsCode);
        return ResponseEntity.ok(ApiResponse.ok("상권 상세 조회 성공", response));
    }
}
```

- [ ] **Step 9: 테스트 통과 확인**

Run: `./gradlew test --tests "bigbang.butilkka_be.region.RegionDetailServiceTest" --tests "bigbang.butilkka_be.region.RegionDetailControllerTest" --console=plain`
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 10: 커밋**

```bash
git add src/main/java/bigbang/butilkka_be/region src/test/java/bigbang/butilkka_be/region
git commit -m "Add GET /api/v1/districts/{districtsCode} region detail endpoint"
```

---

### Task 8: 관심 상권(즐겨찾기) CRUD

**Files:**
- Modify: `src/main/java/bigbang/butilkka_be/user/UserInterestRegionRepository.java`
- Create: `src/main/java/bigbang/butilkka_be/user/dto/FavoriteAddRequest.java`
- Create: `src/main/java/bigbang/butilkka_be/user/dto/FavoriteItem.java`
- Create: `src/main/java/bigbang/butilkka_be/user/FavoriteService.java`
- Create: `src/main/java/bigbang/butilkka_be/user/FavoriteController.java`
- Test: `src/test/java/bigbang/butilkka_be/user/FavoriteServiceTest.java`
- Test: `src/test/java/bigbang/butilkka_be/user/FavoriteControllerTest.java`

**Interfaces:**
- Consumes: `RegionRepository`, `DistrictRepository` (기존), `CommercialStatsQueryService.latestForRegion(String)` (Task 3)
- Produces: `FavoriteService.add/list/remove` — 이 태스크로 완결.

- [ ] **Step 1: Repository에 개수/삭제 조회 메서드 추가**

`src/main/java/bigbang/butilkka_be/user/UserInterestRegionRepository.java` 전체를 아래로 교체:

```java
package bigbang.butilkka_be.user;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface UserInterestRegionRepository extends JpaRepository<UserInterestRegion, Long> {
    List<UserInterestRegion> findByUserId(Long userId);
    Optional<UserInterestRegion> findByUserIdAndRegionCode(Long userId, String regionCode);
}
```

- [ ] **Step 2: DTO 작성**

`src/main/java/bigbang/butilkka_be/user/dto/FavoriteAddRequest.java`:

```java
package bigbang.butilkka_be.user.dto;

public record FavoriteAddRequest(String regionCode) {}
```

`src/main/java/bigbang/butilkka_be/user/dto/FavoriteItem.java`:

```java
package bigbang.butilkka_be.user.dto;

public record FavoriteItem(String regionCode, String regionName, String district, String grade) {}
```

- [ ] **Step 3: 실패하는 서비스 테스트 작성**

`src/test/java/bigbang/butilkka_be/user/FavoriteServiceTest.java`:

```java
package bigbang.butilkka_be.user;

import bigbang.butilkka_be.common.exception.AppException;
import bigbang.butilkka_be.region.District;
import bigbang.butilkka_be.region.DistrictRepository;
import bigbang.butilkka_be.region.Region;
import bigbang.butilkka_be.region.RegionRepository;
import bigbang.butilkka_be.stats.CommercialStats;
import bigbang.butilkka_be.stats.CommercialStatsQueryService;
import bigbang.butilkka_be.user.dto.FavoriteItem;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FavoriteServiceTest {

    @Mock
    private UserInterestRegionRepository userInterestRegionRepository;
    @Mock
    private RegionRepository regionRepository;
    @Mock
    private DistrictRepository districtRepository;
    @Mock
    private CommercialStatsQueryService commercialStatsQueryService;

    @InjectMocks
    private FavoriteService favoriteService;

    private Region regionOf(String code, String name, String districtCode) {
        Region region = mock(Region.class);
        when(region.getRegionCode()).thenReturn(code);
        when(region.getRegionName()).thenReturn(name);
        when(region.getDistrictCode()).thenReturn(districtCode);
        return region;
    }

    @Test
    void add_withValidRegion_createsFavorite() {
        when(userInterestRegionRepository.findByUserId(1L)).thenReturn(List.of());
        when(userInterestRegionRepository.findByUserIdAndRegionCode(1L, "1168064000")).thenReturn(Optional.empty());
        when(regionRepository.findById("1168064000")).thenReturn(Optional.of(regionOf("1168064000", "역삼1동", "11680")));
        District district = mock(District.class);
        when(district.getDistrictName()).thenReturn("강남구");
        when(districtRepository.findById("11680")).thenReturn(Optional.of(district));

        FavoriteItem result = favoriteService.add(1L, "1168064000");

        assertThat(result.regionName()).isEqualTo("역삼1동");
        assertThat(result.district()).isEqualTo("강남구");
        verify(userInterestRegionRepository, times(1)).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void add_withUnknownRegion_throwsBadRequest() {
        when(userInterestRegionRepository.findByUserId(1L)).thenReturn(List.of());
        when(userInterestRegionRepository.findByUserIdAndRegionCode(1L, "UNKNOWN")).thenReturn(Optional.empty());
        when(regionRepository.findById("UNKNOWN")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> favoriteService.add(1L, "UNKNOWN"))
                .isInstanceOf(AppException.class)
                .extracting(e -> ((AppException) e).getHttpStatus())
                .isEqualTo(org.springframework.http.HttpStatus.BAD_REQUEST);
    }

    @Test
    void add_whenAlreadyThreeFavorites_throwsConflict() {
        UserInterestRegion existing1 = UserInterestRegion.create(1L, "A", null, 1);
        UserInterestRegion existing2 = UserInterestRegion.create(1L, "B", null, 2);
        UserInterestRegion existing3 = UserInterestRegion.create(1L, "C", null, 3);
        when(userInterestRegionRepository.findByUserId(1L)).thenReturn(List.of(existing1, existing2, existing3));

        assertThatThrownBy(() -> favoriteService.add(1L, "1168064000"))
                .isInstanceOf(AppException.class)
                .extracting(e -> ((AppException) e).getHttpStatus())
                .isEqualTo(org.springframework.http.HttpStatus.CONFLICT);
    }

    @Test
    void add_whenAlreadyFavorited_throwsConflict() {
        when(userInterestRegionRepository.findByUserId(1L)).thenReturn(List.of());
        when(userInterestRegionRepository.findByUserIdAndRegionCode(1L, "1168064000"))
                .thenReturn(Optional.of(UserInterestRegion.create(1L, "1168064000", null, 1)));

        assertThatThrownBy(() -> favoriteService.add(1L, "1168064000"))
                .isInstanceOf(AppException.class)
                .extracting(e -> ((AppException) e).getHttpStatus())
                .isEqualTo(org.springframework.http.HttpStatus.CONFLICT);
    }

    @Test
    void list_returnsFavoritesWithLatestGrade() {
        UserInterestRegion favorite = UserInterestRegion.create(1L, "1168064000", null, 1);
        when(userInterestRegionRepository.findByUserId(1L)).thenReturn(List.of(favorite));
        when(regionRepository.findById("1168064000")).thenReturn(Optional.of(regionOf("1168064000", "역삼1동", "11680")));
        District district = mock(District.class);
        when(district.getDistrictName()).thenReturn("강남구");
        when(districtRepository.findById("11680")).thenReturn(Optional.of(district));
        CommercialStats stats = mock(CommercialStats.class);
        when(stats.getDeclineGrade()).thenReturn("A");
        when(commercialStatsQueryService.latestForRegion("1168064000")).thenReturn(Optional.of(stats));

        List<FavoriteItem> result = favoriteService.list(1L);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).grade()).isEqualTo("A");
    }

    @Test
    void remove_withRegisteredFavorite_deletesIt() {
        UserInterestRegion favorite = UserInterestRegion.create(1L, "1168064000", null, 1);
        when(userInterestRegionRepository.findByUserIdAndRegionCode(1L, "1168064000")).thenReturn(Optional.of(favorite));

        favoriteService.remove(1L, "1168064000");

        verify(userInterestRegionRepository, times(1)).delete(favorite);
    }

    @Test
    void remove_withUnregisteredFavorite_throwsNotFound() {
        when(userInterestRegionRepository.findByUserIdAndRegionCode(1L, "1168064000")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> favoriteService.remove(1L, "1168064000"))
                .isInstanceOf(AppException.class)
                .extracting(e -> ((AppException) e).getHttpStatus())
                .isEqualTo(org.springframework.http.HttpStatus.NOT_FOUND);
    }
}
```

- [ ] **Step 4: 테스트가 실패하는지 확인**

Run: `./gradlew test --tests "bigbang.butilkka_be.user.FavoriteServiceTest" --console=plain`
Expected: FAIL — `FavoriteService` 클래스가 존재하지 않음 (컴파일 에러)

- [ ] **Step 5: `FavoriteService` 구현**

`src/main/java/bigbang/butilkka_be/user/FavoriteService.java`:

```java
package bigbang.butilkka_be.user;

import bigbang.butilkka_be.common.exception.AppException;
import bigbang.butilkka_be.region.District;
import bigbang.butilkka_be.region.DistrictRepository;
import bigbang.butilkka_be.region.Region;
import bigbang.butilkka_be.region.RegionRepository;
import bigbang.butilkka_be.stats.CommercialStatsQueryService;
import bigbang.butilkka_be.user.dto.FavoriteItem;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class FavoriteService {

    private static final int MAX_FAVORITES = 3;

    private final UserInterestRegionRepository userInterestRegionRepository;
    private final RegionRepository regionRepository;
    private final DistrictRepository districtRepository;
    private final CommercialStatsQueryService commercialStatsQueryService;

    @Transactional
    public FavoriteItem add(Long userId, String regionCode) {
        if (userInterestRegionRepository.findByUserId(userId).size() >= MAX_FAVORITES) {
            throw AppException.conflict("관심 상권은 최대 3개까지 등록할 수 있습니다.");
        }
        if (userInterestRegionRepository.findByUserIdAndRegionCode(userId, regionCode).isPresent()) {
            throw AppException.conflict("이미 등록된 관심 상권입니다.");
        }

        Region region = regionRepository.findById(regionCode)
                .orElseThrow(() -> AppException.badRequest("존재하지 않는 상권코드입니다."));
        District district = districtRepository.findById(region.getDistrictCode())
                .orElseThrow(() -> AppException.notFound("존재하지 않는 자치구코드입니다."));

        int nextSortOrder = userInterestRegionRepository.findByUserId(userId).size() + 1;
        UserInterestRegion favorite = UserInterestRegion.create(userId, regionCode, region.getRegionName(), nextSortOrder);
        userInterestRegionRepository.save(favorite);

        return new FavoriteItem(region.getRegionCode(), region.getRegionName(), district.getDistrictName(), null);
    }

    public List<FavoriteItem> list(Long userId) {
        return userInterestRegionRepository.findByUserId(userId).stream()
                .map(this::toFavoriteItem)
                .toList();
    }

    @Transactional
    public void remove(Long userId, String regionCode) {
        UserInterestRegion favorite = userInterestRegionRepository.findByUserIdAndRegionCode(userId, regionCode)
                .orElseThrow(() -> AppException.notFound("등록되지 않은 관심 상권입니다."));
        userInterestRegionRepository.delete(favorite);
    }

    private FavoriteItem toFavoriteItem(UserInterestRegion favorite) {
        Region region = regionRepository.findById(favorite.getRegionCode())
                .orElseThrow(() -> AppException.notFound("존재하지 않는 상권코드입니다."));
        District district = districtRepository.findById(region.getDistrictCode())
                .orElseThrow(() -> AppException.notFound("존재하지 않는 자치구코드입니다."));
        String grade = commercialStatsQueryService.latestForRegion(region.getRegionCode())
                .map(bigbang.butilkka_be.stats.CommercialStats::getDeclineGrade)
                .orElse(null);
        return new FavoriteItem(region.getRegionCode(), region.getRegionName(), district.getDistrictName(), grade);
    }
}
```

- [ ] **Step 6: 서비스 테스트 통과 확인**

Run: `./gradlew test --tests "bigbang.butilkka_be.user.FavoriteServiceTest" --console=plain`
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 7: 실패하는 컨트롤러 테스트 작성**

`@AuthenticationPrincipal`을 쓰는 컨트롤러는 `@AutoConfigureMockMvc(addFilters = false)`만으로는 `SecurityMockMvcRequestPostProcessors.authentication(...)`이 동작하지 않는다 (`@WebMvcTest`가 앱의 커스텀 `SecurityConfig`/`SecurityFilterChain`을 로드하지 않아 principal이 항상 null로 resolve됨 — 온보딩 플랜 Task 7에서 실제로 겪은 문제). 그래서 아래처럼 `@Import(SecurityConfig.class)`를 추가하고 `addFilters`는 기본값(true)으로 둔다.

`src/test/java/bigbang/butilkka_be/user/FavoriteControllerTest.java`:

```java
package bigbang.butilkka_be.user;

import bigbang.butilkka_be.common.security.JwtTokenProvider;
import bigbang.butilkka_be.common.security.SecurityConfig;
import bigbang.butilkka_be.user.dto.FavoriteItem;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(FavoriteController.class)
@AutoConfigureMockMvc
@Import(SecurityConfig.class)
class FavoriteControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private FavoriteService favoriteService;

    @MockitoBean
    private JwtTokenProvider jwtTokenProvider;

    private static Authentication authAs(String userId) {
        return new UsernamePasswordAuthenticationToken(userId, null, List.of());
    }

    @Test
    void add_returnsCreated() throws Exception {
        when(favoriteService.add(eq(1L), eq("1168064000")))
                .thenReturn(new FavoriteItem("1168064000", "역삼1동", "강남구", null));

        mockMvc.perform(post("/api/v1/favorites")
                        .with(authentication(authAs("1")))
                        .contentType("application/json")
                        .content("{\"regionCode\": \"1168064000\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.regionName").value("역삼1동"));
    }

    @Test
    void list_returnsOk() throws Exception {
        when(favoriteService.list(1L)).thenReturn(List.of(new FavoriteItem("1168064000", "역삼1동", "강남구", "A")));

        mockMvc.perform(get("/api/v1/favorites").with(authentication(authAs("1"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].regionName").value("역삼1동"));
    }

    @Test
    void remove_returnsOk() throws Exception {
        mockMvc.perform(delete("/api/v1/favorites/1168064000").with(authentication(authAs("1"))))
                .andExpect(status().isOk());
    }
}
```

- [ ] **Step 8: 테스트가 실패하는지 확인**

Run: `./gradlew test --tests "bigbang.butilkka_be.user.FavoriteControllerTest" --console=plain`
Expected: FAIL — `FavoriteController` 클래스가 존재하지 않음 (컴파일 에러)

- [ ] **Step 9: `FavoriteController` 구현**

`src/main/java/bigbang/butilkka_be/user/FavoriteController.java`:

```java
package bigbang.butilkka_be.user;

import bigbang.butilkka_be.common.response.ApiResponse;
import bigbang.butilkka_be.user.dto.FavoriteAddRequest;
import bigbang.butilkka_be.user.dto.FavoriteItem;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/favorites")
@RequiredArgsConstructor
public class FavoriteController {

    private final FavoriteService favoriteService;

    @PostMapping
    public ResponseEntity<ApiResponse<FavoriteItem>> add(
            @AuthenticationPrincipal String userId,
            @RequestBody FavoriteAddRequest request) {
        FavoriteItem item = favoriteService.add(Long.parseLong(userId), request.regionCode());
        return ResponseEntity.status(201).body(ApiResponse.created("관심 상권 추가 성공", item));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<FavoriteItem>>> list(
            @AuthenticationPrincipal String userId) {
        List<FavoriteItem> items = favoriteService.list(Long.parseLong(userId));
        return ResponseEntity.ok(ApiResponse.ok("관심 상권 조회 성공", items));
    }

    @DeleteMapping("/{regionCode}")
    public ResponseEntity<ApiResponse<Void>> remove(
            @AuthenticationPrincipal String userId,
            @PathVariable String regionCode) {
        favoriteService.remove(Long.parseLong(userId), regionCode);
        return ResponseEntity.ok(ApiResponse.ok("관심 상권 삭제 성공", null));
    }
}
```

- [ ] **Step 10: 테스트 통과 확인**

Run: `./gradlew test --tests "bigbang.butilkka_be.user.FavoriteServiceTest" --tests "bigbang.butilkka_be.user.FavoriteControllerTest" --console=plain`
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 11: 커밋**

```bash
git add src/main/java/bigbang/butilkka_be/user src/test/java/bigbang/butilkka_be/user/FavoriteServiceTest.java src/test/java/bigbang/butilkka_be/user/FavoriteControllerTest.java
git commit -m "Add favorites CRUD (POST/GET/DELETE /api/v1/favorites)"
```

---

### Task 9: 전체 검증

**Files:** 없음 (기존 파일 재확인만 수행)

- [ ] **Step 1: 전체 테스트 스위트 실행**

Run: `./gradlew test --console=plain`
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 2: 앱 실행 후 인증 없이 7개 엔드포인트가 401을 반환하는지 확인**

Run: `docker compose up -d mysql`
Run: `DB_URL="jdbc:mysql://localhost:3307/butilkka?serverTimezone=Asia/Seoul&characterEncoding=UTF-8" ./gradlew bootRun --console=plain` (백그라운드 실행)

Run: `curl -s -o /dev/null -w "%{http_code}\n" "http://localhost:8080/api/v1/regions/map"`
Expected: `401`

Run: `curl -s -o /dev/null -w "%{http_code}\n" "http://localhost:8080/api/v1/regions/declineRanking?order=top"`
Expected: `401`

Run: `curl -s -o /dev/null -w "%{http_code}\n" "http://localhost:8080/api/v1/regions/search?keyword=역삼"`
Expected: `401`

Run: `curl -s -o /dev/null -w "%{http_code}\n" "http://localhost:8080/api/v1/districts/1168064000"`
Expected: `401`

Run: `curl -s -o /dev/null -w "%{http_code}\n" -X POST "http://localhost:8080/api/v1/favorites" -H "Content-Type: application/json" -d '{}'`
Expected: `401`

- [ ] **Step 3: 앱 종료**

- [ ] **Step 4: 최종 상태 확인**

Run: `git log --oneline -12`
Expected: Task 1~8의 커밋(V20, V21, CommercialStatsQueryService, map, declineRanking, search, district detail, favorites)이 순서대로 보임
