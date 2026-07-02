# 리포트 화면 API Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 리포트 화면의 API 4개(최신 분기 리포트, 리포트 히스토리 목록, 특정 리포트 상세, 유사 사례 전체 목록)를 구현한다.

**Architecture:** 이미 존재하는 `Report`/`ReportCause`/`ReportSignal`/`ReportSimilarCase`/`ReportAlternativeRegion` 엔티티·리포지토리를 그대로 사용하고, 스펙 대비 부족한 3개 컬럼(`reports.year`/`reports.score`/`report_cause.description`/`report_alternative_regions.stat`)만 마이그레이션으로 추가한다. `report` 패키지에 신규 서비스 3개(`ReportDetailService`, `ReportHistoryService`, `ReportCaseService`)와 컨트롤러 2개(`ReportController`, `ReportHistoryController`)를 추가한다.

**Tech Stack:** Spring Boot 4.1, Spring Data JPA, Flyway, MySQL 8.0 (Docker), JUnit5 + Mockito + AssertJ + MockMvc, Spring Security Test.

## Global Constraints

- 모든 신규 에러는 기존 `AppException`(notFound) + `GlobalExceptionHandler` 컨벤션을 따른다.
- 모든 신규 응답은 `ApiResponse.ok(message, data)` 포맷을 따른다.
- 신규 엔드포인트는 `SecurityConfig`의 `anyRequest().authenticated()`에 자동 포함되므로 `SecurityConfig` 변경은 불필요하다.
- `@AuthenticationPrincipal`을 사용하는 컨트롤러 테스트는 `@AutoConfigureMockMvc(addFilters = false)`를 쓰지 않고 `@Import(SecurityConfig.class)` + 기본 `@AutoConfigureMockMvc` + `SecurityMockMvcRequestPostProcessors.authentication(...)`을 사용한다.
- `quarter` 응답 필드는 `"{year}Q{quarter}"` 형식 문자열로 조합한다 (예: `"2026Q4"`).
- 다른 유저 소유의 `reportId`에 접근하거나 존재하지 않는 `reportId`에 접근하면 동일하게 404 `"존재하지 않는 리포트입니다."`를 반환한다 (존재 여부를 숨김).
- `/reports/latest`에서 유저의 리포트가 하나도 없으면 404 `"생성된 리포트가 없습니다."`.
- `/reports/{reportId}`와 `/reports/latest`의 `similarCases`는 미리보기로 최대 3건만 포함한다. `/reports/{reportId}/cases`는 전체 목록을 offset/limit으로 페이징한다.
- `/reportsHistory`는 `totalCount`+`hasNext`를 포함하고, `/reports/{reportId}/cases`는 `totalCount`만 포함한다 (`hasNext` 없음).
- `report_decision_reasons` 테이블/엔티티는 이번 4개 API 어디에도 노출하지 않는다.

---

### Task 1: `reports`/`report_cause`/`report_alternative_regions`에 컬럼 추가 (V22)

**Files:**
- Modify: `src/main/java/bigbang/butilkka_be/report/Report.java`
- Modify: `src/main/java/bigbang/butilkka_be/report/ReportCause.java`
- Modify: `src/main/java/bigbang/butilkka_be/report/ReportAlternativeRegion.java`
- Create: `src/main/resources/db/migration/V22__add_year_score_description_stat_to_reports.sql`

**Interfaces:**
- Produces: `Report.getYear(): Integer`, `Report.getScore(): Integer`, `ReportCause.getDescription(): String`, `ReportAlternativeRegion.getStat(): String` — Task 2, 4에서 사용.

- [ ] **Step 1: 마이그레이션 작성**

`src/main/resources/db/migration/V22__add_year_score_description_stat_to_reports.sql`:

```sql
-- reports: year, score 추가
ALTER TABLE reports
    ADD COLUMN year SMALLINT NOT NULL DEFAULT 2026 COMMENT '연도' AFTER quarter;
ALTER TABLE reports
    ALTER COLUMN year DROP DEFAULT;

ALTER TABLE reports
    ADD COLUMN score TINYINT NULL COMMENT '상권 점수 (0~100)' AFTER grade;

UPDATE reports SET score = CASE grade
    WHEN 'A' THEN 90
    WHEN 'B' THEN 70
    WHEN 'C' THEN 50
    WHEN 'D' THEN 30
    WHEN 'E' THEN 10
END;

ALTER TABLE reports MODIFY COLUMN score TINYINT NOT NULL COMMENT '상권 점수 (0~100)';

-- report_cause: description 추가
ALTER TABLE report_cause
    ADD COLUMN description VARCHAR(255) NULL COMMENT '원인 설명' AFTER level;

UPDATE report_cause SET description = '연말 프로모션과 모임 수요 증가로 매출이 늘고 있습니다' WHERE report_id = 1 AND title = '연말 시즌 소비 증가';
UPDATE report_cause SET description = '인근 오피스 근무자 유입이 늘어 유동인구가 증가했습니다' WHERE report_id = 1 AND title = '직장인 유동인구 증가';
UPDATE report_cause SET description = '인근에 신규 오피스가 들어서며 잠재 고객층이 확대되었습니다' WHERE report_id = 1 AND title = '신규 오피스 빌딩 입주';
UPDATE report_cause SET description = '외국인 관광객 방문이 늘며 매출에 긍정적 영향을 주고 있습니다' WHERE report_id = 2 AND title = '관광객 유입 증가';
UPDATE report_cause SET description = '감성 카페 트렌드 확산으로 신규 고객 유입이 늘고 있습니다' WHERE report_id = 2 AND title = '카페 트렌드 변화';
UPDATE report_cause SET description = '전반적인 소비 심리 위축으로 매출이 감소하고 있습니다' WHERE report_id = 3 AND title = '경기 침체 영향';
UPDATE report_cause SET description = '배달앱 내 경쟁 점포 증가로 주문이 분산되고 있습니다' WHERE report_id = 3 AND title = '배달앱 경쟁 심화';
UPDATE report_cause SET description = '치킨 원육 등 원자재 가격 상승으로 수익성이 악화되고 있습니다' WHERE report_id = 3 AND title = '원자재 가격 상승';
UPDATE report_cause SET description = '성수동 상권 전체의 인기 상승에 따른 낙수 효과가 나타나고 있습니다' WHERE report_id = 4 AND title = '성수동 핫플 효과';
UPDATE report_cause SET description = '20대 고객 비중이 늘며 트렌디한 서비스 수요가 증가했습니다' WHERE report_id = 4 AND title = '젊은층 유입';
UPDATE report_cause SET description = '유동인구가 소폭 늘며 안정적인 매출 기반이 유지되고 있습니다' WHERE report_id = 5 AND title = '완만한 유동인구 증가';
UPDATE report_cause SET description = '임대료 변동이 크지 않아 고정비 부담이 안정적입니다' WHERE report_id = 5 AND title = '안정적 임대료 수준';
UPDATE report_cause SET description = '전분기에 이어 매출 상승세가 이어지고 있습니다' WHERE report_id = 6 AND title = '매출 상승 지속';
UPDATE report_cause SET description = '인근 경쟁 점포 수가 줄며 상대적으로 수요를 흡수하고 있습니다' WHERE report_id = 6 AND title = '경쟁 점포 감소';
UPDATE report_cause SET description = '계절적 비수기로 유동인구와 매출이 일시적으로 감소했습니다' WHERE report_id = 7 AND title = '여름 비수기 영향';
UPDATE report_cause SET description = '비수기 영향으로 인근 폐업률이 소폭 상승했습니다' WHERE report_id = 7 AND title = '일시적 폐업률 상승';

ALTER TABLE report_cause MODIFY COLUMN description VARCHAR(255) NOT NULL COMMENT '원인 설명';

-- report_alternative_regions: stat 추가
ALTER TABLE report_alternative_regions
    ADD COLUMN stat VARCHAR(50) NULL COMMENT '핵심 지표' AFTER reason;

UPDATE report_alternative_regions SET stat = '유동인구 +12.8%' WHERE report_id = 3 AND region_code = '1120065000';
UPDATE report_alternative_regions SET stat = '임대료 -15.3%' WHERE report_id = 3 AND region_code = '1171058000';
UPDATE report_alternative_regions SET stat = '폐업률 -2.1%' WHERE report_id = 3 AND region_code = '1144069000';
UPDATE report_alternative_regions SET stat = '유동인구 +1.2%' WHERE report_id = 7 AND region_code = '1168065000';
UPDATE report_alternative_regions SET stat = '유동인구 +3.5%' WHERE report_id = 7 AND region_code = '1168058000';

ALTER TABLE report_alternative_regions MODIFY COLUMN stat VARCHAR(50) NOT NULL COMMENT '핵심 지표';
```

- [ ] **Step 2: 엔티티에 필드 추가**

`src/main/java/bigbang/butilkka_be/report/Report.java`의 `quarter` 필드 선언 바로 뒤에 추가:

```java
    @Column(nullable = false, columnDefinition = "SMALLINT")
    private Integer year;
```

같은 파일의 `grade` 필드 선언 바로 뒤에 추가:

```java
    @Column(nullable = false, columnDefinition = "TINYINT")
    private Integer score;
```

`src/main/java/bigbang/butilkka_be/report/ReportCause.java`의 `level` 필드 선언 바로 뒤(클래스 닫는 `}` 앞)에 추가:

```java

    @Column(nullable = false, length = 255)
    private String description;
```

`src/main/java/bigbang/butilkka_be/report/ReportAlternativeRegion.java`의 `reason` 필드 선언 바로 뒤(클래스 닫는 `}` 앞)에 추가:

```java

    @Column(nullable = false, length = 50)
    private String stat;
```

- [ ] **Step 3: 앱 실행해 마이그레이션 적용 확인**

Run: `docker compose up -d mysql` (이미 떠 있으면 스킵)
Run: `./gradlew bootRun --console=plain` (백그라운드 실행)
Expected 로그: `Migrating schema \`butilkka\` to version "22 - add year score description stat to reports"`, `Started ButilkkaBeApplication`

- [ ] **Step 4: DB 확인**

Run: `docker exec butilkka-be-mysql-1 mysql -uroot -p1234 -e "SELECT report_id, year, score FROM butilkka.reports ORDER BY report_id;"`
Expected: 7개 행 모두 `year=2026`, `score`는 grade에 맞는 값(A=90/B=70/C=50 등)

Run: `docker exec butilkka-be-mysql-1 mysql -uroot -p1234 -e "SELECT COUNT(*) FROM butilkka.report_cause WHERE description IS NULL;"`
Expected: `0`

Run: `docker exec butilkka-be-mysql-1 mysql -uroot -p1234 -e "SELECT COUNT(*) FROM butilkka.report_alternative_regions WHERE stat IS NULL;"`
Expected: `0`

앱을 종료한다.

- [ ] **Step 5: 커밋**

```bash
git add src/main/java/bigbang/butilkka_be/report src/main/resources/db/migration/V22__add_year_score_description_stat_to_reports.sql
git commit -m "Add year/score to reports, description to report_cause, stat to report_alternative_regions"
```

---

### Task 2: `GET /api/v1/reports/latest`, `GET /api/v1/reports/{reportId}`

**Files:**
- Create: `src/main/java/bigbang/butilkka_be/report/dto/ReportDetailResponse.java`
- Create: `src/main/java/bigbang/butilkka_be/report/ReportDetailService.java`
- Create: `src/main/java/bigbang/butilkka_be/report/ReportController.java`
- Test: `src/test/java/bigbang/butilkka_be/report/ReportDetailServiceTest.java`
- Test: `src/test/java/bigbang/butilkka_be/report/ReportControllerTest.java`

**Interfaces:**
- Consumes: `Report.getYear()`, `Report.getScore()`, `ReportCause.getDescription()`, `ReportAlternativeRegion.getStat()` (Task 1); 기존 `ReportRepository.findByUserId(Long)`, `ReportCauseRepository.findByReportId(Long)`, `ReportSignalRepository.findByReportId(Long)`, `ReportSimilarCaseRepository.findByReportId(Long)`, `ReportAlternativeRegionRepository.findByReportId(Long)`, `RegionRepository.findById(String)`, `DistrictRepository.findById(String)`, `CategoryRepository.findById(String)`
- Produces: `ReportDetailService.getLatest(Long userId): ReportDetailResponse`, `ReportDetailService.getDetail(Long userId, Long reportId): ReportDetailResponse` — Task 4에서 `ReportController`를 수정할 때 이 파일 구조를 따른다.

- [ ] **Step 1: DTO 작성**

`src/main/java/bigbang/butilkka_be/report/dto/ReportDetailResponse.java`:

```java
package bigbang.butilkka_be.report.dto;

import java.util.List;

public record ReportDetailResponse(
        Long reportId,
        String regionCode,
        String districtName,
        String regionName,
        String categoryName,
        String quarter,
        String grade,
        String declineType,
        Integer score,
        String briefing,
        String aiOutlook,
        List<Cause> causes,
        List<Signal> leadingSignals,
        List<SimilarCasePreview> similarCases,
        Decision decision,
        List<AlternativeRegion> alternativeRegions
) {
    public record Cause(String title, String level, String description) {}

    public record Signal(String title, String description) {}

    public record Period(int startYear, int endYear) {}

    public record SimilarCasePreview(String caseId, String regionCode, String regionName, String summary, Period period) {}

    public record Decision(String recommendation, String title, String description) {}

    public record AlternativeRegion(int rank, String regionCode, String regionName, String reason, String stat) {}
}
```

- [ ] **Step 2: 실패하는 서비스 테스트 작성**

`src/test/java/bigbang/butilkka_be/report/ReportDetailServiceTest.java`:

```java
package bigbang.butilkka_be.report;

import bigbang.butilkka_be.category.Category;
import bigbang.butilkka_be.category.CategoryRepository;
import bigbang.butilkka_be.common.exception.AppException;
import bigbang.butilkka_be.region.District;
import bigbang.butilkka_be.region.DistrictRepository;
import bigbang.butilkka_be.region.Region;
import bigbang.butilkka_be.region.RegionRepository;
import bigbang.butilkka_be.report.dto.ReportDetailResponse;
import org.junit.jupiter.api.BeforeEach;
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
class ReportDetailServiceTest {

    @Mock
    private ReportRepository reportRepository;
    @Mock
    private ReportCauseRepository reportCauseRepository;
    @Mock
    private ReportSignalRepository reportSignalRepository;
    @Mock
    private ReportSimilarCaseRepository reportSimilarCaseRepository;
    @Mock
    private ReportAlternativeRegionRepository reportAlternativeRegionRepository;
    @Mock
    private RegionRepository regionRepository;
    @Mock
    private DistrictRepository districtRepository;
    @Mock
    private CategoryRepository categoryRepository;

    private ReportDetailService service;

    @BeforeEach
    void setUp() {
        service = new ReportDetailService(
                reportRepository, reportCauseRepository, reportSignalRepository,
                reportSimilarCaseRepository, reportAlternativeRegionRepository,
                regionRepository, districtRepository, categoryRepository);
    }

    private static Report reportOf(Long reportId, Long userId, int year, int quarter, String grade, int score) {
        Report report = mock(Report.class);
        when(report.getReportId()).thenReturn(reportId);
        when(report.getUserId()).thenReturn(userId);
        when(report.getRegionCode()).thenReturn("1168064000");
        when(report.getCategoryCode()).thenReturn("CS100001");
        when(report.getYear()).thenReturn(year);
        when(report.getQuarter()).thenReturn(quarter);
        when(report.getGrade()).thenReturn(grade);
        when(report.getDeclineType()).thenReturn("성장형");
        when(report.getScore()).thenReturn(score);
        when(report.getSummary()).thenReturn("한 줄 브리핑");
        when(report.getAiOutlook()).thenReturn("AI 전망");
        when(report.getDecisionRecommendation()).thenReturn("버티기");
        when(report.getDecisionTitle()).thenReturn("현 위치 유지 권장");
        when(report.getDecisionDescription()).thenReturn("의사결정 설명");
        return report;
    }

    private void stubRegionAndCategory() {
        Region region = mock(Region.class);
        when(region.getRegionName()).thenReturn("역삼1동");
        when(region.getDistrictCode()).thenReturn("11680");
        when(regionRepository.findById("1168064000")).thenReturn(Optional.of(region));

        District district = mock(District.class);
        when(district.getDistrictName()).thenReturn("강남구");
        when(districtRepository.findById("11680")).thenReturn(Optional.of(district));

        Category category = mock(Category.class);
        when(category.getCategoryName()).thenReturn("한식음식점");
        when(categoryRepository.findById("CS100001")).thenReturn(Optional.of(category));
    }

    @Test
    void getLatest_picksHighestYearQuarter() {
        Report older = reportOf(5L, 1L, 2026, 1, "B", 70);
        Report newer = reportOf(1L, 1L, 2026, 4, "A", 90);
        when(reportRepository.findByUserId(1L)).thenReturn(List.of(older, newer));
        stubRegionAndCategory();
        when(reportCauseRepository.findByReportId(1L)).thenReturn(List.of());
        when(reportSignalRepository.findByReportId(1L)).thenReturn(List.of());
        when(reportSimilarCaseRepository.findByReportId(1L)).thenReturn(List.of());
        when(reportAlternativeRegionRepository.findByReportId(1L)).thenReturn(List.of());

        ReportDetailResponse response = service.getLatest(1L);

        assertThat(response.reportId()).isEqualTo(1L);
        assertThat(response.quarter()).isEqualTo("2026Q4");
        assertThat(response.regionName()).isEqualTo("역삼1동");
        assertThat(response.districtName()).isEqualTo("강남구");
        assertThat(response.categoryName()).isEqualTo("한식음식점");
        assertThat(response.score()).isEqualTo(90);
        assertThat(response.decision().recommendation()).isEqualTo("버티기");
    }

    @Test
    void getLatest_withNoReports_throwsNotFound() {
        when(reportRepository.findByUserId(1L)).thenReturn(List.of());

        assertThatThrownBy(() -> service.getLatest(1L))
                .isInstanceOf(AppException.class)
                .extracting(e -> ((AppException) e).getHttpStatus())
                .isEqualTo(org.springframework.http.HttpStatus.NOT_FOUND);
    }

    @Test
    void getDetail_withOwnedReport_returnsDetail() {
        Report report = reportOf(1L, 1L, 2026, 4, "A", 90);
        when(reportRepository.findById(1L)).thenReturn(Optional.of(report));
        stubRegionAndCategory();
        when(reportCauseRepository.findByReportId(1L)).thenReturn(List.of());
        when(reportSignalRepository.findByReportId(1L)).thenReturn(List.of());
        when(reportSimilarCaseRepository.findByReportId(1L)).thenReturn(List.of());
        when(reportAlternativeRegionRepository.findByReportId(1L)).thenReturn(List.of());

        ReportDetailResponse response = service.getDetail(1L, 1L);

        assertThat(response.reportId()).isEqualTo(1L);
    }

    @Test
    void getDetail_withOtherUsersReport_throwsNotFound() {
        Report report = reportOf(1L, 2L, 2026, 4, "A", 90);
        when(reportRepository.findById(1L)).thenReturn(Optional.of(report));

        assertThatThrownBy(() -> service.getDetail(1L, 1L))
                .isInstanceOf(AppException.class)
                .extracting(e -> ((AppException) e).getHttpStatus())
                .isEqualTo(org.springframework.http.HttpStatus.NOT_FOUND);
    }

    @Test
    void getDetail_withUnknownReportId_throwsNotFound() {
        when(reportRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getDetail(1L, 99L))
                .isInstanceOf(AppException.class)
                .extracting(e -> ((AppException) e).getHttpStatus())
                .isEqualTo(org.springframework.http.HttpStatus.NOT_FOUND);
    }

    @Test
    void getDetail_buildsCausesSignalsAndAlternativeRegionsWithRank() {
        Report report = reportOf(3L, 1L, 2026, 4, "C", 50);
        when(reportRepository.findById(3L)).thenReturn(Optional.of(report));
        stubRegionAndCategory();

        ReportCause cause = mock(ReportCause.class);
        when(cause.getTitle()).thenReturn("경기 침체 영향");
        when(cause.getLevel()).thenReturn("높음");
        when(cause.getDescription()).thenReturn("전반적인 소비 심리 위축으로 매출이 감소하고 있습니다");
        when(reportCauseRepository.findByReportId(3L)).thenReturn(List.of(cause));

        ReportSignal signal = mock(ReportSignal.class);
        when(signal.getTitle()).thenReturn("폐업률 고위험");
        when(signal.getDescription()).thenReturn("폐업률 6.8%로 여전히 높은 수준");
        when(reportSignalRepository.findByReportId(3L)).thenReturn(List.of(signal));

        when(reportSimilarCaseRepository.findByReportId(3L)).thenReturn(List.of());

        ReportAlternativeRegion alt1 = mock(ReportAlternativeRegion.class);
        when(alt1.getRegionCode()).thenReturn("1120065000");
        when(alt1.getReason()).thenReturn("성수동: 성장세 지속 중, 젊은 고객층 유입 활발");
        when(alt1.getStat()).thenReturn("유동인구 +12.8%");
        Region altRegion1 = mock(Region.class);
        when(altRegion1.getRegionName()).thenReturn("성수1가1동");
        when(regionRepository.findById("1120065000")).thenReturn(Optional.of(altRegion1));

        ReportAlternativeRegion alt2 = mock(ReportAlternativeRegion.class);
        when(alt2.getRegionCode()).thenReturn("1171058000");
        when(alt2.getReason()).thenReturn("송리단길: 신흥 상권으로 임대료 합리적, 성장 가능성 높음");
        when(alt2.getStat()).thenReturn("임대료 -15.3%");
        Region altRegion2 = mock(Region.class);
        when(altRegion2.getRegionName()).thenReturn("잠실본동");
        when(regionRepository.findById("1171058000")).thenReturn(Optional.of(altRegion2));

        when(reportAlternativeRegionRepository.findByReportId(3L)).thenReturn(List.of(alt1, alt2));

        ReportDetailResponse response = service.getDetail(1L, 3L);

        assertThat(response.causes()).hasSize(1);
        assertThat(response.causes().get(0).description()).isEqualTo("전반적인 소비 심리 위축으로 매출이 감소하고 있습니다");
        assertThat(response.leadingSignals()).hasSize(1);
        assertThat(response.alternativeRegions()).hasSize(2);
        assertThat(response.alternativeRegions().get(0).rank()).isEqualTo(1);
        assertThat(response.alternativeRegions().get(0).regionName()).isEqualTo("성수1가1동");
        assertThat(response.alternativeRegions().get(1).rank()).isEqualTo(2);
        assertThat(response.alternativeRegions().get(1).stat()).isEqualTo("임대료 -15.3%");
    }

    @Test
    void getDetail_limitsSimilarCasesToThree() {
        Report report = reportOf(1L, 1L, 2026, 4, "A", 90);
        when(reportRepository.findById(1L)).thenReturn(Optional.of(report));
        stubRegionAndCategory();
        when(reportCauseRepository.findByReportId(1L)).thenReturn(List.of());
        when(reportSignalRepository.findByReportId(1L)).thenReturn(List.of());
        when(reportAlternativeRegionRepository.findByReportId(1L)).thenReturn(List.of());

        ReportSimilarCase case1 = similarCaseOf("case-1", "1168051000", "사례1");
        ReportSimilarCase case2 = similarCaseOf("case-2", "1168051000", "사례2");
        ReportSimilarCase case3 = similarCaseOf("case-3", "1168051000", "사례3");
        ReportSimilarCase case4 = similarCaseOf("case-4", "1168051000", "사례4");
        when(reportSimilarCaseRepository.findByReportId(1L)).thenReturn(List.of(case1, case2, case3, case4));

        Region caseRegion = mock(Region.class);
        when(caseRegion.getRegionName()).thenReturn("신사동");
        when(regionRepository.findById("1168051000")).thenReturn(Optional.of(caseRegion));

        ReportDetailResponse response = service.getDetail(1L, 1L);

        assertThat(response.similarCases()).hasSize(3);
        assertThat(response.similarCases().get(0).caseId()).isEqualTo("case-1");
    }

    private static ReportSimilarCase similarCaseOf(String id, String regionCode, String summary) {
        ReportSimilarCase c = mock(ReportSimilarCase.class);
        when(c.getId()).thenReturn(id);
        when(c.getRegionCode()).thenReturn(regionCode);
        when(c.getSummary()).thenReturn(summary);
        when(c.getStartYear()).thenReturn((short) 2019);
        when(c.getEndYear()).thenReturn((short) 2022);
        return c;
    }
}
```

- [ ] **Step 3: 테스트가 실패하는지 확인**

Run: `./gradlew test --tests "bigbang.butilkka_be.report.ReportDetailServiceTest" --console=plain`
Expected: FAIL — `ReportDetailService` 클래스가 존재하지 않음 (컴파일 에러)

- [ ] **Step 4: `ReportDetailService` 구현**

`src/main/java/bigbang/butilkka_be/report/ReportDetailService.java`:

```java
package bigbang.butilkka_be.report;

import bigbang.butilkka_be.category.Category;
import bigbang.butilkka_be.category.CategoryRepository;
import bigbang.butilkka_be.common.exception.AppException;
import bigbang.butilkka_be.region.District;
import bigbang.butilkka_be.region.DistrictRepository;
import bigbang.butilkka_be.region.Region;
import bigbang.butilkka_be.region.RegionRepository;
import bigbang.butilkka_be.report.dto.ReportDetailResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ReportDetailService {

    private final ReportRepository reportRepository;
    private final ReportCauseRepository reportCauseRepository;
    private final ReportSignalRepository reportSignalRepository;
    private final ReportSimilarCaseRepository reportSimilarCaseRepository;
    private final ReportAlternativeRegionRepository reportAlternativeRegionRepository;
    private final RegionRepository regionRepository;
    private final DistrictRepository districtRepository;
    private final CategoryRepository categoryRepository;

    public ReportDetailResponse getLatest(Long userId) {
        Report latest = reportRepository.findByUserId(userId).stream()
                .max(Comparator.comparingInt(Report::getYear).thenComparingInt(Report::getQuarter))
                .orElseThrow(() -> AppException.notFound("생성된 리포트가 없습니다."));
        return buildDetail(latest);
    }

    public ReportDetailResponse getDetail(Long userId, Long reportId) {
        Report report = reportRepository.findById(reportId)
                .filter(r -> r.getUserId().equals(userId))
                .orElseThrow(() -> AppException.notFound("존재하지 않는 리포트입니다."));
        return buildDetail(report);
    }

    private ReportDetailResponse buildDetail(Report report) {
        Region region = regionRepository.findById(report.getRegionCode())
                .orElseThrow(() -> AppException.notFound("존재하지 않는 상권코드입니다."));
        District district = districtRepository.findById(region.getDistrictCode())
                .orElseThrow(() -> AppException.notFound("존재하지 않는 자치구코드입니다."));
        String categoryName = report.getCategoryCode() == null ? null
                : categoryRepository.findById(report.getCategoryCode())
                        .map(Category::getCategoryName)
                        .orElseThrow(() -> AppException.notFound("존재하지 않는 업종코드입니다."));

        List<ReportDetailResponse.Cause> causes = reportCauseRepository.findByReportId(report.getReportId()).stream()
                .map(c -> new ReportDetailResponse.Cause(c.getTitle(), c.getLevel(), c.getDescription()))
                .toList();

        List<ReportDetailResponse.Signal> signals = reportSignalRepository.findByReportId(report.getReportId()).stream()
                .map(s -> new ReportDetailResponse.Signal(s.getTitle(), s.getDescription()))
                .toList();

        List<ReportDetailResponse.SimilarCasePreview> similarCases = reportSimilarCaseRepository.findByReportId(report.getReportId()).stream()
                .limit(3)
                .map(this::toSimilarCasePreview)
                .toList();

        List<ReportAlternativeRegion> alternativeRegionEntities = reportAlternativeRegionRepository.findByReportId(report.getReportId());
        List<ReportDetailResponse.AlternativeRegion> alternativeRegions = new ArrayList<>();
        for (int i = 0; i < alternativeRegionEntities.size(); i++) {
            alternativeRegions.add(toAlternativeRegion(alternativeRegionEntities.get(i), i + 1));
        }

        ReportDetailResponse.Decision decision = new ReportDetailResponse.Decision(
                report.getDecisionRecommendation(), report.getDecisionTitle(), report.getDecisionDescription());

        return new ReportDetailResponse(
                report.getReportId(),
                report.getRegionCode(),
                district.getDistrictName(),
                region.getRegionName(),
                categoryName,
                report.getYear() + "Q" + report.getQuarter(),
                report.getGrade(),
                report.getDeclineType(),
                report.getScore(),
                report.getSummary(),
                report.getAiOutlook(),
                causes,
                signals,
                similarCases,
                decision,
                alternativeRegions
        );
    }

    private ReportDetailResponse.SimilarCasePreview toSimilarCasePreview(ReportSimilarCase c) {
        Region region = regionRepository.findById(c.getRegionCode())
                .orElseThrow(() -> AppException.notFound("존재하지 않는 상권코드입니다."));
        return new ReportDetailResponse.SimilarCasePreview(
                c.getId(), c.getRegionCode(), region.getRegionName(), c.getSummary(),
                new ReportDetailResponse.Period(c.getStartYear(), c.getEndYear()));
    }

    private ReportDetailResponse.AlternativeRegion toAlternativeRegion(ReportAlternativeRegion a, int rank) {
        Region region = regionRepository.findById(a.getRegionCode())
                .orElseThrow(() -> AppException.notFound("존재하지 않는 상권코드입니다."));
        return new ReportDetailResponse.AlternativeRegion(rank, a.getRegionCode(), region.getRegionName(), a.getReason(), a.getStat());
    }
}
```

- [ ] **Step 5: 서비스 테스트 통과 확인**

Run: `./gradlew test --tests "bigbang.butilkka_be.report.ReportDetailServiceTest" --console=plain`
Expected: `BUILD SUCCESSFUL`, 7개 테스트 모두 PASS

- [ ] **Step 6: 실패하는 컨트롤러 테스트 작성**

`src/test/java/bigbang/butilkka_be/report/ReportControllerTest.java`:

```java
package bigbang.butilkka_be.report;

import bigbang.butilkka_be.common.security.JwtTokenProvider;
import bigbang.butilkka_be.common.security.SecurityConfig;
import bigbang.butilkka_be.report.dto.ReportDetailResponse;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ReportController.class)
@AutoConfigureMockMvc
@Import(SecurityConfig.class)
class ReportControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ReportDetailService reportDetailService;

    @MockitoBean
    private JwtTokenProvider jwtTokenProvider;

    private static Authentication authAs(String userId) {
        return new UsernamePasswordAuthenticationToken(userId, null, List.of());
    }

    private static ReportDetailResponse sampleResponse() {
        return new ReportDetailResponse(
                1L, "1168064000", "강남구", "역삼1동", "한식음식점",
                "2026Q4", "A", "성장형", 90, "한 줄 브리핑", "AI 전망",
                List.of(), List.of(), List.of(),
                new ReportDetailResponse.Decision("버티기", "현 위치 유지 권장", "의사결정 설명"),
                List.of());
    }

    @Test
    void getLatest_returnsOk() throws Exception {
        when(reportDetailService.getLatest(eq(1L))).thenReturn(sampleResponse());

        mockMvc.perform(get("/api/v1/reports/latest")
                        .with(authentication(authAs("1"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.regionName").value("역삼1동"));
    }

    @Test
    void getDetail_returnsOk() throws Exception {
        when(reportDetailService.getDetail(eq(1L), eq(1L))).thenReturn(sampleResponse());

        mockMvc.perform(get("/api/v1/reports/1")
                        .with(authentication(authAs("1"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.quarter").value("2026Q4"));
    }
}
```

- [ ] **Step 7: 테스트가 실패하는지 확인**

Run: `./gradlew test --tests "bigbang.butilkka_be.report.ReportControllerTest" --console=plain`
Expected: FAIL — `ReportController` 클래스가 존재하지 않음 (컴파일 에러)

- [ ] **Step 8: `ReportController` 구현**

`src/main/java/bigbang/butilkka_be/report/ReportController.java`:

```java
package bigbang.butilkka_be.report;

import bigbang.butilkka_be.common.response.ApiResponse;
import bigbang.butilkka_be.report.dto.ReportDetailResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/reports")
@RequiredArgsConstructor
public class ReportController {

    private final ReportDetailService reportDetailService;

    @GetMapping("/latest")
    public ResponseEntity<ApiResponse<ReportDetailResponse>> getLatest(
            @AuthenticationPrincipal String userId) {
        ReportDetailResponse response = reportDetailService.getLatest(Long.parseLong(userId));
        return ResponseEntity.ok(ApiResponse.ok("리포트 조회 성공", response));
    }

    @GetMapping("/{reportId}")
    public ResponseEntity<ApiResponse<ReportDetailResponse>> getDetail(
            @AuthenticationPrincipal String userId,
            @PathVariable Long reportId) {
        ReportDetailResponse response = reportDetailService.getDetail(Long.parseLong(userId), reportId);
        return ResponseEntity.ok(ApiResponse.ok("리포트 상세 조회 성공", response));
    }
}
```

- [ ] **Step 9: 테스트 통과 확인**

Run: `./gradlew test --tests "bigbang.butilkka_be.report.*" --console=plain`
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 10: 커밋**

```bash
git add src/main/java/bigbang/butilkka_be/report src/test/java/bigbang/butilkka_be/report
git commit -m "Add GET /api/v1/reports/latest and GET /api/v1/reports/{reportId} endpoints"
```

---

### Task 3: `GET /api/v1/reportsHistory`

**Files:**
- Create: `src/main/java/bigbang/butilkka_be/report/dto/ReportHistoryResponse.java`
- Create: `src/main/java/bigbang/butilkka_be/report/ReportHistoryService.java`
- Create: `src/main/java/bigbang/butilkka_be/report/ReportHistoryController.java`
- Test: `src/test/java/bigbang/butilkka_be/report/ReportHistoryServiceTest.java`
- Test: `src/test/java/bigbang/butilkka_be/report/ReportHistoryControllerTest.java`

**Interfaces:**
- Consumes: 기존 `ReportRepository.findByUserId(Long)`; `Report.getYear()`/`getScore()` (Task 1)
- Produces: `ReportHistoryService.getHistory(Long userId, int offset, int limit): ReportHistoryResponse` — 이 태스크로 완결 (다른 태스크가 재사용하지 않음).

- [ ] **Step 1: DTO 작성**

`src/main/java/bigbang/butilkka_be/report/dto/ReportHistoryResponse.java`:

```java
package bigbang.butilkka_be.report.dto;

import java.util.List;

public record ReportHistoryResponse(
        int totalCount,
        boolean hasNext,
        List<ReportHistoryItem> reports
) {
    public record ReportHistoryItem(Long reportId, String quarter, String grade, String briefing) {}
}
```

- [ ] **Step 2: 실패하는 서비스 테스트 작성**

`src/test/java/bigbang/butilkka_be/report/ReportHistoryServiceTest.java`:

```java
package bigbang.butilkka_be.report;

import bigbang.butilkka_be.report.dto.ReportHistoryResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReportHistoryServiceTest {

    @Mock
    private ReportRepository reportRepository;

    private ReportHistoryService service;

    @BeforeEach
    void setUp() {
        service = new ReportHistoryService(reportRepository);
    }

    private static Report reportOf(Long reportId, int year, int quarter, String grade, String summary) {
        Report report = mock(Report.class);
        when(report.getReportId()).thenReturn(reportId);
        when(report.getYear()).thenReturn(year);
        when(report.getQuarter()).thenReturn(quarter);
        when(report.getGrade()).thenReturn(grade);
        when(report.getSummary()).thenReturn(summary);
        return report;
    }

    @Test
    void getHistory_sortsNewestFirst() {
        Report q1 = reportOf(5L, 2026, 1, "B", "1분기 요약");
        Report q4 = reportOf(1L, 2026, 4, "A", "4분기 요약");
        Report q2 = reportOf(6L, 2026, 2, "A", "2분기 요약");
        when(reportRepository.findByUserId(1L)).thenReturn(List.of(q1, q4, q2));

        ReportHistoryResponse response = service.getHistory(1L, 0, 20);

        assertThat(response.totalCount()).isEqualTo(3);
        assertThat(response.hasNext()).isFalse();
        assertThat(response.reports()).hasSize(3);
        assertThat(response.reports().get(0).reportId()).isEqualTo(1L);
        assertThat(response.reports().get(0).quarter()).isEqualTo("2026Q4");
        assertThat(response.reports().get(2).reportId()).isEqualTo(5L);
    }

    @Test
    void getHistory_appliesOffsetAndLimit() {
        Report q1 = reportOf(5L, 2026, 1, "B", "1분기 요약");
        Report q2 = reportOf(6L, 2026, 2, "A", "2분기 요약");
        Report q3 = reportOf(7L, 2026, 3, "C", "3분기 요약");
        Report q4 = reportOf(1L, 2026, 4, "A", "4분기 요약");
        when(reportRepository.findByUserId(1L)).thenReturn(List.of(q1, q2, q3, q4));

        ReportHistoryResponse response = service.getHistory(1L, 1, 2);

        assertThat(response.totalCount()).isEqualTo(4);
        assertThat(response.hasNext()).isTrue();
        assertThat(response.reports()).hasSize(2);
        assertThat(response.reports().get(0).reportId()).isEqualTo(7L);
        assertThat(response.reports().get(1).reportId()).isEqualTo(6L);
    }

    @Test
    void getHistory_withNoReports_returnsEmptyList() {
        when(reportRepository.findByUserId(1L)).thenReturn(List.of());

        ReportHistoryResponse response = service.getHistory(1L, 0, 20);

        assertThat(response.totalCount()).isEqualTo(0);
        assertThat(response.hasNext()).isFalse();
        assertThat(response.reports()).isEmpty();
    }
}
```

- [ ] **Step 3: 테스트가 실패하는지 확인**

Run: `./gradlew test --tests "bigbang.butilkka_be.report.ReportHistoryServiceTest" --console=plain`
Expected: FAIL — `ReportHistoryService` 클래스가 존재하지 않음 (컴파일 에러)

- [ ] **Step 4: `ReportHistoryService` 구현**

`src/main/java/bigbang/butilkka_be/report/ReportHistoryService.java`:

```java
package bigbang.butilkka_be.report;

import bigbang.butilkka_be.report.dto.ReportHistoryResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ReportHistoryService {

    private final ReportRepository reportRepository;

    public ReportHistoryResponse getHistory(Long userId, int offset, int limit) {
        List<Report> sorted = reportRepository.findByUserId(userId).stream()
                .sorted(Comparator.comparingInt(Report::getYear).thenComparingInt(Report::getQuarter).reversed())
                .toList();

        List<ReportHistoryResponse.ReportHistoryItem> page = sorted.stream()
                .skip(offset)
                .limit(limit)
                .map(r -> new ReportHistoryResponse.ReportHistoryItem(
                        r.getReportId(), r.getYear() + "Q" + r.getQuarter(), r.getGrade(), r.getSummary()))
                .toList();

        boolean hasNext = offset + page.size() < sorted.size();

        return new ReportHistoryResponse(sorted.size(), hasNext, page);
    }
}
```

- [ ] **Step 5: 서비스 테스트 통과 확인**

Run: `./gradlew test --tests "bigbang.butilkka_be.report.ReportHistoryServiceTest" --console=plain`
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 6: 실패하는 컨트롤러 테스트 작성**

`src/test/java/bigbang/butilkka_be/report/ReportHistoryControllerTest.java`:

```java
package bigbang.butilkka_be.report;

import bigbang.butilkka_be.common.security.JwtTokenProvider;
import bigbang.butilkka_be.common.security.SecurityConfig;
import bigbang.butilkka_be.report.dto.ReportHistoryResponse;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ReportHistoryController.class)
@AutoConfigureMockMvc
@Import(SecurityConfig.class)
class ReportHistoryControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ReportHistoryService reportHistoryService;

    @MockitoBean
    private JwtTokenProvider jwtTokenProvider;

    private static Authentication authAs(String userId) {
        return new UsernamePasswordAuthenticationToken(userId, null, List.of());
    }

    @Test
    void getHistory_returnsOk() throws Exception {
        when(reportHistoryService.getHistory(eq(1L), eq(0), eq(20))).thenReturn(
                new ReportHistoryResponse(1, false, List.of(
                        new ReportHistoryResponse.ReportHistoryItem(1L, "2026Q4", "A", "요약"))));

        mockMvc.perform(get("/api/v1/reportsHistory")
                        .with(authentication(authAs("1"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.reports[0].grade").value("A"));
    }

    @Test
    void getHistory_withOffsetAndLimit_passesParamsThrough() throws Exception {
        when(reportHistoryService.getHistory(eq(1L), eq(2), eq(5))).thenReturn(
                new ReportHistoryResponse(10, true, List.of()));

        mockMvc.perform(get("/api/v1/reportsHistory")
                        .param("offset", "2")
                        .param("limit", "5")
                        .with(authentication(authAs("1"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalCount").value(10))
                .andExpect(jsonPath("$.data.hasNext").value(true));
    }
}
```

- [ ] **Step 7: 테스트가 실패하는지 확인**

Run: `./gradlew test --tests "bigbang.butilkka_be.report.ReportHistoryControllerTest" --console=plain`
Expected: FAIL — `ReportHistoryController` 클래스가 존재하지 않음 (컴파일 에러)

- [ ] **Step 8: `ReportHistoryController` 구현**

`src/main/java/bigbang/butilkka_be/report/ReportHistoryController.java`:

```java
package bigbang.butilkka_be.report;

import bigbang.butilkka_be.common.response.ApiResponse;
import bigbang.butilkka_be.report.dto.ReportHistoryResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/reportsHistory")
@RequiredArgsConstructor
public class ReportHistoryController {

    private final ReportHistoryService reportHistoryService;

    @GetMapping
    public ResponseEntity<ApiResponse<ReportHistoryResponse>> getHistory(
            @AuthenticationPrincipal String userId,
            @RequestParam(defaultValue = "0") int offset,
            @RequestParam(defaultValue = "20") int limit) {
        ReportHistoryResponse response = reportHistoryService.getHistory(Long.parseLong(userId), offset, limit);
        return ResponseEntity.ok(ApiResponse.ok("리포트 히스토리 조회 성공", response));
    }
}
```

- [ ] **Step 9: 테스트 통과 확인**

Run: `./gradlew test --tests "bigbang.butilkka_be.report.*" --console=plain`
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 10: 커밋**

```bash
git add src/main/java/bigbang/butilkka_be/report src/test/java/bigbang/butilkka_be/report
git commit -m "Add GET /api/v1/reportsHistory endpoint"
```

---

### Task 4: `GET /api/v1/reports/{reportId}/cases`

**Files:**
- Create: `src/main/java/bigbang/butilkka_be/report/dto/ReportCaseListResponse.java`
- Create: `src/main/java/bigbang/butilkka_be/report/ReportCaseService.java`
- Modify: `src/main/java/bigbang/butilkka_be/report/ReportController.java` (Task 2에서 생성)
- Test: `src/test/java/bigbang/butilkka_be/report/ReportCaseServiceTest.java`
- Test: Modify `src/test/java/bigbang/butilkka_be/report/ReportControllerTest.java` (Task 2에서 생성)

**Interfaces:**
- Consumes: 기존 `ReportRepository.findById(Long)`, `ReportSimilarCaseRepository.findByReportId(Long)`, `RegionRepository.findById(String)`
- Produces: `ReportCaseService.getCases(Long userId, Long reportId, int offset, int limit): ReportCaseListResponse`

- [ ] **Step 1: DTO 작성**

`src/main/java/bigbang/butilkka_be/report/dto/ReportCaseListResponse.java`:

```java
package bigbang.butilkka_be.report.dto;

import java.util.List;

public record ReportCaseListResponse(
        int totalCount,
        List<ReportCaseItem> cases
) {
    public record ReportCaseItem(
            String caseId,
            String regionCode,
            String regionName,
            String summary,
            String description,
            String tag1,
            String tag2,
            String tag3,
            String tag4,
            Period period
    ) {}

    public record Period(int startYear, int endYear) {}
}
```

- [ ] **Step 2: 실패하는 서비스 테스트 작성**

`src/test/java/bigbang/butilkka_be/report/ReportCaseServiceTest.java`:

```java
package bigbang.butilkka_be.report;

import bigbang.butilkka_be.common.exception.AppException;
import bigbang.butilkka_be.region.Region;
import bigbang.butilkka_be.region.RegionRepository;
import bigbang.butilkka_be.report.dto.ReportCaseListResponse;
import org.junit.jupiter.api.BeforeEach;
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
class ReportCaseServiceTest {

    @Mock
    private ReportRepository reportRepository;
    @Mock
    private ReportSimilarCaseRepository reportSimilarCaseRepository;
    @Mock
    private RegionRepository regionRepository;

    private ReportCaseService service;

    @BeforeEach
    void setUp() {
        service = new ReportCaseService(reportRepository, reportSimilarCaseRepository, regionRepository);
    }

    private static Report reportOf(Long reportId, Long userId) {
        Report report = mock(Report.class);
        when(report.getReportId()).thenReturn(reportId);
        when(report.getUserId()).thenReturn(userId);
        return report;
    }

    private static ReportSimilarCase caseOf(String id, String regionCode) {
        ReportSimilarCase c = mock(ReportSimilarCase.class);
        when(c.getId()).thenReturn(id);
        when(c.getRegionCode()).thenReturn(regionCode);
        when(c.getSummary()).thenReturn("요약");
        when(c.getDescription()).thenReturn("상세 설명");
        when(c.getTag1()).thenReturn("태그1");
        when(c.getTag2()).thenReturn("태그2");
        when(c.getTag3()).thenReturn("태그3");
        when(c.getTag4()).thenReturn("태그4");
        when(c.getStartYear()).thenReturn((short) 2019);
        when(c.getEndYear()).thenReturn((short) 2022);
        return c;
    }

    @Test
    void getCases_returnsAllFieldsWithRegionName() {
        when(reportRepository.findById(1L)).thenReturn(Optional.of(reportOf(1L, 1L)));
        ReportSimilarCase c = caseOf("case-1", "1168051000");
        when(reportSimilarCaseRepository.findByReportId(1L)).thenReturn(List.of(c));
        Region region = mock(Region.class);
        when(region.getRegionName()).thenReturn("신사동");
        when(regionRepository.findById("1168051000")).thenReturn(Optional.of(region));

        ReportCaseListResponse response = service.getCases(1L, 1L, 0, 20);

        assertThat(response.totalCount()).isEqualTo(1);
        assertThat(response.cases()).hasSize(1);
        assertThat(response.cases().get(0).regionName()).isEqualTo("신사동");
        assertThat(response.cases().get(0).tag1()).isEqualTo("태그1");
        assertThat(response.cases().get(0).period().startYear()).isEqualTo(2019);
    }

    @Test
    void getCases_appliesOffsetAndLimit() {
        when(reportRepository.findById(1L)).thenReturn(Optional.of(reportOf(1L, 1L)));
        ReportSimilarCase c1 = caseOf("case-1", "1168051000");
        ReportSimilarCase c2 = caseOf("case-2", "1168051000");
        ReportSimilarCase c3 = caseOf("case-3", "1168051000");
        when(reportSimilarCaseRepository.findByReportId(1L)).thenReturn(List.of(c1, c2, c3));
        Region region = mock(Region.class);
        when(region.getRegionName()).thenReturn("신사동");
        when(regionRepository.findById("1168051000")).thenReturn(Optional.of(region));

        ReportCaseListResponse response = service.getCases(1L, 1L, 1, 1);

        assertThat(response.totalCount()).isEqualTo(3);
        assertThat(response.cases()).hasSize(1);
        assertThat(response.cases().get(0).caseId()).isEqualTo("case-2");
    }

    @Test
    void getCases_withOtherUsersReport_throwsNotFound() {
        when(reportRepository.findById(1L)).thenReturn(Optional.of(reportOf(1L, 2L)));

        assertThatThrownBy(() -> service.getCases(1L, 1L, 0, 20))
                .isInstanceOf(AppException.class)
                .extracting(e -> ((AppException) e).getHttpStatus())
                .isEqualTo(org.springframework.http.HttpStatus.NOT_FOUND);
    }

    @Test
    void getCases_withUnknownReportId_throwsNotFound() {
        when(reportRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getCases(1L, 99L, 0, 20))
                .isInstanceOf(AppException.class)
                .extracting(e -> ((AppException) e).getHttpStatus())
                .isEqualTo(org.springframework.http.HttpStatus.NOT_FOUND);
    }
}
```

- [ ] **Step 3: 테스트가 실패하는지 확인**

Run: `./gradlew test --tests "bigbang.butilkka_be.report.ReportCaseServiceTest" --console=plain`
Expected: FAIL — `ReportCaseService` 클래스가 존재하지 않음 (컴파일 에러)

- [ ] **Step 4: `ReportCaseService` 구현**

`src/main/java/bigbang/butilkka_be/report/ReportCaseService.java`:

```java
package bigbang.butilkka_be.report;

import bigbang.butilkka_be.common.exception.AppException;
import bigbang.butilkka_be.region.Region;
import bigbang.butilkka_be.region.RegionRepository;
import bigbang.butilkka_be.report.dto.ReportCaseListResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ReportCaseService {

    private final ReportRepository reportRepository;
    private final ReportSimilarCaseRepository reportSimilarCaseRepository;
    private final RegionRepository regionRepository;

    public ReportCaseListResponse getCases(Long userId, Long reportId, int offset, int limit) {
        Report report = reportRepository.findById(reportId)
                .filter(r -> r.getUserId().equals(userId))
                .orElseThrow(() -> AppException.notFound("존재하지 않는 리포트입니다."));

        List<ReportSimilarCase> all = reportSimilarCaseRepository.findByReportId(report.getReportId());

        List<ReportCaseListResponse.ReportCaseItem> page = all.stream()
                .skip(offset)
                .limit(limit)
                .map(this::toCaseItem)
                .toList();

        return new ReportCaseListResponse(all.size(), page);
    }

    private ReportCaseListResponse.ReportCaseItem toCaseItem(ReportSimilarCase c) {
        Region region = regionRepository.findById(c.getRegionCode())
                .orElseThrow(() -> AppException.notFound("존재하지 않는 상권코드입니다."));
        return new ReportCaseListResponse.ReportCaseItem(
                c.getId(), c.getRegionCode(), region.getRegionName(), c.getSummary(), c.getDescription(),
                c.getTag1(), c.getTag2(), c.getTag3(), c.getTag4(),
                new ReportCaseListResponse.Period(c.getStartYear(), c.getEndYear()));
    }
}
```

- [ ] **Step 5: 서비스 테스트 통과 확인**

Run: `./gradlew test --tests "bigbang.butilkka_be.report.ReportCaseServiceTest" --console=plain`
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 6: 컨트롤러 테스트에 케이스 테스트 추가 (실패 확인)**

`src/test/java/bigbang/butilkka_be/report/ReportControllerTest.java`의 `@MockitoBean private ReportDetailService reportDetailService;` 바로 뒤에 필드 추가:

```java
    @MockitoBean
    private ReportCaseService reportCaseService;
```

같은 파일의 마지막 `@Test` 메서드 뒤(클래스 닫는 `}` 앞)에 테스트 추가:

```java

    @Test
    void getCases_returnsOk() throws Exception {
        when(reportCaseService.getCases(eq(1L), eq(1L), eq(0), eq(20))).thenReturn(
                new bigbang.butilkka_be.report.dto.ReportCaseListResponse(1, List.of(
                        new bigbang.butilkka_be.report.dto.ReportCaseListResponse.ReportCaseItem(
                                "case-1", "1168051000", "신사동", "요약", "상세 설명",
                                "태그1", "태그2", "태그3", "태그4",
                                new bigbang.butilkka_be.report.dto.ReportCaseListResponse.Period(2019, 2022)))));

        mockMvc.perform(get("/api/v1/reports/1/cases")
                        .with(authentication(authAs("1"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.cases[0].regionName").value("신사동"));
    }
```

Run: `./gradlew test --tests "bigbang.butilkka_be.report.ReportControllerTest" --console=plain`
Expected: FAIL — `ReportController`에 `/{reportId}/cases` 매핑이 없어 404, 그리고 `ReportCaseService`가 `ReportController`의 생성자 의존성이 아니라서 `@MockitoBean`이 컨텍스트에 연결되지 않아 컴파일/컨텍스트 로딩 에러

- [ ] **Step 7: `ReportController`에 엔드포인트 추가**

`src/main/java/bigbang/butilkka_be/report/ReportController.java` 전체를 아래로 교체:

```java
package bigbang.butilkka_be.report;

import bigbang.butilkka_be.common.response.ApiResponse;
import bigbang.butilkka_be.report.dto.ReportCaseListResponse;
import bigbang.butilkka_be.report.dto.ReportDetailResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/reports")
@RequiredArgsConstructor
public class ReportController {

    private final ReportDetailService reportDetailService;
    private final ReportCaseService reportCaseService;

    @GetMapping("/latest")
    public ResponseEntity<ApiResponse<ReportDetailResponse>> getLatest(
            @AuthenticationPrincipal String userId) {
        ReportDetailResponse response = reportDetailService.getLatest(Long.parseLong(userId));
        return ResponseEntity.ok(ApiResponse.ok("리포트 조회 성공", response));
    }

    @GetMapping("/{reportId}")
    public ResponseEntity<ApiResponse<ReportDetailResponse>> getDetail(
            @AuthenticationPrincipal String userId,
            @PathVariable Long reportId) {
        ReportDetailResponse response = reportDetailService.getDetail(Long.parseLong(userId), reportId);
        return ResponseEntity.ok(ApiResponse.ok("리포트 상세 조회 성공", response));
    }

    @GetMapping("/{reportId}/cases")
    public ResponseEntity<ApiResponse<ReportCaseListResponse>> getCases(
            @AuthenticationPrincipal String userId,
            @PathVariable Long reportId,
            @RequestParam(defaultValue = "0") int offset,
            @RequestParam(defaultValue = "20") int limit) {
        ReportCaseListResponse response = reportCaseService.getCases(Long.parseLong(userId), reportId, offset, limit);
        return ResponseEntity.ok(ApiResponse.ok("유사 사례 조회 성공", response));
    }
}
```

- [ ] **Step 8: 테스트 통과 확인**

Run: `./gradlew test --tests "bigbang.butilkka_be.report.*" --console=plain`
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 9: 커밋**

```bash
git add src/main/java/bigbang/butilkka_be/report src/test/java/bigbang/butilkka_be/report
git commit -m "Add GET /api/v1/reports/{reportId}/cases endpoint"
```

---

### Task 5: 전체 검증

**Files:** 없음 (기존 파일 재확인만 수행)

- [ ] **Step 1: 전체 테스트 스위트 실행**

Run: `./gradlew test --console=plain`
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 2: 앱 실행 후 인증 없이 4개 엔드포인트가 401 또는 403을 반환하는지 확인**

Run: `docker compose up -d mysql`
Run: `./gradlew bootRun --console=plain` (백그라운드 실행)

Run: `curl -s -o /dev/null -w "%{http_code}\n" "http://localhost:8080/api/v1/reports/latest"`
Expected: `401` 또는 `403` (Spring Security 기본 익명 거부 동작 — 이전 플랜들에서 이미 확인된 것과 동일)

Run: `curl -s -o /dev/null -w "%{http_code}\n" "http://localhost:8080/api/v1/reportsHistory"`
Expected: `401` 또는 `403`

Run: `curl -s -o /dev/null -w "%{http_code}\n" "http://localhost:8080/api/v1/reports/1"`
Expected: `401` 또는 `403`

Run: `curl -s -o /dev/null -w "%{http_code}\n" "http://localhost:8080/api/v1/reports/1/cases"`
Expected: `401` 또는 `403`

- [ ] **Step 3: 앱 종료**

- [ ] **Step 4: 최종 상태 확인**

Run: `git log --oneline -8`
Expected: Task 1~4의 커밋(V22 마이그레이션, latest/detail, history, cases)이 순서대로 보임
