# 리포트 화면 API 구현 설계

## 배경

노션 "API명세서 V2" 기준 리포트 화면의 API 4개를 구현한다:

- `GET /api/v1/reports/latest` — 내 가게 최신 분기 리포트(종합)
- `GET /api/v1/reportsHistory` — 리포트 히스토리 목록
- `GET /api/v1/reports/{reportId}` — 특정 리포트 상세
- `GET /api/v1/reports/{reportId}/cases` — 유사 사례 전체 목록

리포트 도메인은 이미 온보딩/맵-상권 플랜 이전부터 엔티티·리포지토리·마이그레이션(V9~V14, V17, V19)이 만들어져 있다: `Report`, `ReportCause`, `ReportSignal`, `ReportDecisionReasons`, `ReportSimilarCase`, `ReportAlternativeRegion`. 이번 작업은 주로 이 기존 데이터를 조회해 응답 DTO로 가공하는 서비스/컨트롤러 계층을 추가하는 것이지만, 스펙과 기존 스키마를 대조한 결과 3가지 컬럼이 스키마에 없어 마이그레이션이 필요하다.

## 알려진 스펙 이슈 및 결정 사항

1. **`quarter` 필드 형식 불일치**: 스펙 "항목" 목록은 `quarter` Int로 되어 있으나 JSON 예시는 `"2025Q1"` 문자열을 보여준다. 기존 `reports` 테이블엔 `quarter`(TINYINT, 1~4)만 있고 `year` 컬럼이 없다. → **`year` 컬럼을 추가**하고 응답의 `quarter` 필드는 맵/상권 플랜과 동일하게 `"{year}Q{quarter}"` 형식 문자열로 조합한다.
2. **`score`(Int, 0~100) 필드가 `Report` 엔티티에 없음** → `reports.score` 컬럼을 실제로 추가한다.
3. **`causes[].description`이 `report_cause`에 없음**(현재 title/level만 존재) → `report_cause.description` 컬럼을 실제로 추가한다.
4. **`alternativeRegions[].stat`이 `report_alternative_regions`에 없음**(현재 region_code/reason만 존재) → `report_alternative_regions.stat` 컬럼을 실제로 추가한다.
5. **`decision` 객체의 "항목" 목록엔 `recommendation`/`title`만 있지만 예시엔 `description`도 있음**: `Report` 엔티티에 `decisionDescription`이 이미 존재하므로 예시가 맞는 것으로 보고 3개 필드(`recommendation`/`title`/`description`) 모두 반환한다.
6. **다른 유저의 `reportId` 접근 시 처리**: 스펙의 실패 예시가 이미 404 `"존재하지 않는 리포트입니다."`이므로, 존재하지 않는 경우와 다른 유저 소유인 경우를 구분하지 않고 동일하게 404로 응답한다(기존 컨벤션과 동일하게 존재 여부를 숨김).
7. **`report_decision_reasons` 테이블은 이번 4개 API 어디에도 노출되지 않는다.** 스펙에 해당 필드가 없으므로 이번 구현 범위에서 제외한다(테이블/엔티티는 그대로 두되 신규 코드에서 참조하지 않음).

## 1. 데이터 모델 변경 (V22)

기존 시드 데이터(리포트 1~7, 원인 16건, 대체 상권 5건)는 새 컬럼에 맞는 값으로 백필한다.

### 1-1. `reports`에 `year`, `score` 컬럼 추가

- `year` SMALLINT: 기존 7개 행 모두 `2026`으로 백필 (맵/상권 플랜 V20과 동일 패턴: `DEFAULT 2026` 추가 후 `DROP DEFAULT`).
- `score` TINYINT: 기존 행은 `grade` 기준 고정 매핑으로 백필 — A=90, B=70, C=50, D=30, E=10.

### 1-2. `report_cause`에 `description` 컬럼 추가

기존 16개 행에 각 `title`과 어울리는 설명을 handcraft로 채운다 (예: "연말 시즌 소비 증가" → "연말 프로모션과 모임 수요 증가로 매출이 늘고 있습니다").

### 1-3. `report_alternative_regions`에 `stat` 컬럼 추가

기존 5개 행에 `reason`과 어울리는 핵심 지표 문자열을 handcraft로 채운다 (예: "유동인구 +6.2%", "임대료 -8.4%").

## 2. API별 설계

### 2-1. `GET /api/v1/reports/latest`

- 인증된 유저(`@AuthenticationPrincipal`)의 리포트 중 `(year, quarter)`가 가장 큰 것 하나를 반환 (맵/상권 플랜의 `CommercialStatsQueryService.laterOf`와 동일 원리 — `ReportRepository.findByUserId`로 가져와 메모리에서 비교).
- 리포트가 하나도 없으면 404 `"생성된 리포트가 없습니다."`
- 응답 상세 구조는 2-3(특정 리포트 상세)과 동일 — `ReportDetailService`가 공유.

### 2-2. `GET /api/v1/reportsHistory`

- Query: `offset`(기본 0), `limit`(기본 20).
- 인증된 유저의 전체 리포트를 `(year, quarter)` 내림차순(최신 먼저)으로 정렬한 뒤 offset/limit을 메모리에서 적용 (건수가 적어 DB 페이징 불필요 — `RegionRankingService`와 동일 스타일).
- 항목: `reportId`, `quarter`("YYYYQN"), `grade`, `briefing`(=`Report.summary`).
- `totalCount`(전체 개수), `hasNext`(다음 페이지 존재 여부) 포함.

### 2-3. `GET /api/v1/reports/{reportId}`

- PathVariable `reportId`가 인증된 유저 소유가 아니거나 존재하지 않으면 404 `"존재하지 않는 리포트입니다."`
- 응답 구조 (`ReportDetailResponse`):
  - `reportId`, `regionCode`, `districtName`(Region→District 조인), `regionName`(Region 조인), `categoryName`(Category 조인, null 가능)
  - `quarter`("YYYYQN"), `grade`, `declineType`, `score`
  - `briefing`(=`summary`), `aiOutlook`
  - `causes[]`: `title`, `level`, `description` (`ReportCauseRepository.findByReportId`)
  - `leadingSignals[]`: `title`, `description` (`ReportSignalRepository.findByReportId`)
  - `similarCases[]`: 미리보기 최대 3건 — `caseId`, `regionCode`, `regionName`(Region 조인), `summary`, `period{startYear,endYear}` (`ReportSimilarCaseRepository.findByReportId` 결과 중 앞 3개; 전체 목록은 2-4에서 별도 제공)
  - `decision{recommendation,title,description}`: `Report`의 `decisionRecommendation`/`decisionTitle`/`decisionDescription`
  - `alternativeRegions[]`: `rank`(목록 순번, 1부터), `regionCode`, `regionName`(Region 조인), `reason`, `stat` (`ReportAlternativeRegionRepository.findByReportId`)

### 2-4. `GET /api/v1/reports/{reportId}/cases`

- PathVariable `reportId` 소유권 검증은 2-3과 동일 (404 `"존재하지 않는 리포트입니다."`).
- Query: `offset`(기본 0), `limit`(기본 20).
- `ReportSimilarCaseRepository.findByReportId` 전체 결과에 offset/limit 적용.
- 항목(2-3의 미리보기보다 필드가 많음): `caseId`, `regionCode`, `regionName`, `summary`, `description`, `tag1~4`, `period{startYear,endYear}`.
- `totalCount`만 포함하고 `hasNext`는 스펙에 없으므로 포함하지 않는다 (2-2 히스토리와의 차이점).

## 3. 컨트롤러 구조

기존 컨벤션(엔드포인트 그룹당 컨트롤러 하나, `RegionQueryController`/`RegionDetailController`/`FavoriteController` 참고)을 따른다:

- `ReportController` (`@RequestMapping("/api/v1/reports")`): `/latest`, `/{reportId}`, `/{reportId}/cases`
- `ReportHistoryController` (`@RequestMapping("/api/v1/reportsHistory")`): URI가 `/reports` 하위가 아니므로 별도 컨트롤러로 분리

3개 엔드포인트 모두 리포트 소유자 확인을 위해 `@AuthenticationPrincipal`이 필요하므로, 컨트롤러 테스트는 `FavoriteControllerTest`와 동일하게 `@AutoConfigureMockMvc(addFilters = false)` 대신 `@Import(SecurityConfig.class)` + 기본 `@AutoConfigureMockMvc` + `SecurityMockMvcRequestPostProcessors.authentication(...)`을 사용한다.

## 4. 서비스 구조

- `ReportDetailService.getLatest(Long userId): ReportDetailResponse`
- `ReportDetailService.getDetail(Long userId, Long reportId): ReportDetailResponse` — 위 둘은 내부적으로 같은 private 빌더 메서드(`Report` + 연관 4개 리스트 → `ReportDetailResponse`) 공유
- `ReportHistoryService.getHistory(Long userId, int offset, int limit): ReportHistoryResponse`
- `ReportCaseService.getCases(Long userId, Long reportId, int offset, int limit): ReportCaseListResponse`

## 에러 처리

기존 컨벤션 유지: `AppException`(notFound) + `GlobalExceptionHandler` → `ApiResponse` 포맷. 이번 4개 API는 모두 조회(GET)이므로 badRequest/conflict 케이스는 없다.

## 테스트

- `ReportDetailServiceTest`, `ReportControllerTest` (latest/detail 공용 — 리포트 없음 404, 다른 유저 소유 404, 전체 필드 매핑, similarCases 3건 제한)
- `ReportHistoryServiceTest`, `ReportHistoryControllerTest` (정렬, offset/limit, totalCount/hasNext)
- `ReportCaseServiceTest` (`ReportControllerTest`에 케이스 추가) (offset/limit, totalCount, 소유권 404)
