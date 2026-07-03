# 홈 대시보드 API 구현 설계

## 배경

노션 "API명세서 V2" 기준 홈 화면의 API 1개를 구현한다:

- `GET /api/v1/dashboard` — 내 가게 기준 홈 대시보드 (가게 정보, 상권 등급, AI 브리핑, 최근 3분기 핵심 지표)

이 화면은 전용 엔티티가 없다. 대신 `CommercialStats`(지도/상권 화면에서 이미 사용 중)가 스펙이 요구하는 필드를 대부분 이미 가지고 있다: `footTraffic(Delta/Gap)`, `storeCount(Delta/Gap)`, `closureRate(Delta/Gap)`, `declineGrade`, `briefing`. `Report`는 사용하지 않는다 — 분기마다 모든 유저에게 리포트가 생성된다는 보장이 없어, 대시보드가 리포트에 의존하면 "리포트가 없는 유저는 대시보드도 못 본다"는 불필요한 결합이 생긴다. `gaugeValue`(0~100)만 스키마에 없는데, 리포트 화면 V22 마이그레이션에서 이미 쓴 것과 동일한 고정 매핑(A=90/B=70/C=50/D=30/E=10)을 코드 레벨에서 재사용하면 되므로 마이그레이션이 필요 없다.

## 알려진 스펙 이슈 및 결정 사항

1. **`direction`/`delta`/`gap`의 관계**: `CommercialStats`의 `*_delta`/`*_gap` 컬럼은 부호가 있는 값(예: `-2.9`, `-3000`)이지만, 스펙 예시는 `direction: "DOWN"`이면서 `delta: 10`(양수)으로 방향과 크기를 분리해서 보여준다. → 응답의 `delta`/`gap`은 최신 분기 값의 **절댓값**으로 노출하고, `direction`은 부호로 별도 계산한다(양수 또는 0이면 `"UP"`, 음수면 `"DOWN"`).
2. **`grade.previous`가 없는 경우**: 상권 데이터가 1개 분기뿐이면 "지난 분기"가 없다. 스펙에 명시되어 있지 않으므로 이 경우 `previous`는 `null`을 반환한다.
3. **`points[]`가 3개 미만인 경우**: 상권 데이터가 3개 분기 미만이면 있는 만큼만 반환한다(에러 아님).
4. **404 `"등록된 가게 정보가 없습니다."`의 범위**: 유저가 가게를 등록하지 않은 경우(`User.storeRegion == null`)와, 가게는 등록했지만 해당 상권코드의 `CommercialStats` 데이터가 아직 하나도 없는 경우를 **동일하게 404로 처리**한다. 시드 데이터가 특정 상권(약 15개)에만 존재하므로 이 케이스가 실제로 자주 발생할 수 있다 — 두 경우 모두 프론트 입장에서는 "대시보드를 볼 수 없다"는 동일한 결과이므로 스펙의 단일 메시지로 충분하다고 판단했다.
5. **`gaugeValue` 산출**: `CommercialStats.declineGrade`를 리포트 화면과 동일한 고정 매핑(A=90/B=70/C=50/D=30/E=10)으로 변환한다. 마이그레이션 없이 서비스 코드에서 매핑한다.
6. **`metrics`의 3개 지표(`footTraffic`/`storeCount`/`closureRate`) 구조가 스펙상 동일**하므로, 공통 `MetricTrend(direction, delta, gap, points[])` 레코드 하나를 재사용한다.

## 1. `GET /api/v1/dashboard`

- 인증된 유저(`@AuthenticationPrincipal`)의 `User.storeRegion`을 조회한다. `null`이면 404 `"등록된 가게 정보가 없습니다."`.
- `CommercialStatsQueryService.historyForRegion(storeRegion)`으로 해당 상권의 전체 분기 이력(연도/분기 오름차순)을 가져온다. 비어 있으면 404 `"등록된 가게 정보가 없습니다."` (결정 사항 4).
- 최신 분기(리스트의 마지막 원소)에서 `store`, `grade.current`, `grade.gaugeValue`, `briefing`, 3개 지표의 `direction`/`delta`/`gap`을 뽑는다. 두 번째로 최신인 분기(있으면)의 `declineGrade`가 `grade.previous`.
- `points[]`는 이력의 마지막 최대 3개 원소를 `{quarter: "{year}Q{quarter}", value}`로 변환한다 (리포트/마이페이지 화면과 동일한 quarter 포맷).
- 응답 구조 (`DashboardResponse`):
  - `store{regionCode, regionName, categoryName, district}` (Region→District/Category 조인, 기존 `UserService.buildStoreInfo`와 동일 패턴)
  - `grade{current, previous, gaugeValue}`
  - `briefing`
  - `metrics{footTraffic, storeCount, closureRate}` — 각각 `MetricTrend{direction, delta, gap, points[]}`

## 2. 컨트롤러/서비스 구조

기존 컨벤션(엔드포인트 그룹당 컨트롤러 하나)을 따른다:

- `DashboardController` (`@RequestMapping("/api/v1/dashboard")`): `GET`(대시보드 조회 하나뿐)
- `DashboardService`: `getDashboard(Long userId): DashboardResponse`

`@AuthenticationPrincipal`이 필요하므로, 컨트롤러 테스트는 `ReportControllerTest`/`NotificationControllerTest`와 동일하게 `@AutoConfigureMockMvc(addFilters = false)` 대신 `@Import(SecurityConfig.class)` + 기본 `@AutoConfigureMockMvc` + `SecurityMockMvcRequestPostProcessors.authentication(...)`을 사용한다.

## 3. DTO

```java
public record DashboardResponse(
        StoreInfo store,
        Grade grade,
        String briefing,
        Metrics metrics
) {
    public record StoreInfo(String regionCode, String regionName, String categoryName, String district) {}
    public record Grade(String current, String previous, int gaugeValue) {}
    public record Metrics(MetricTrend footTraffic, MetricTrend storeCount, MetricTrend closureRate) {}
    public record MetricTrend(String direction, double delta, long gap, List<Point> points) {}
    public record Point(String quarter, double value) {}
}
```

## 에러 처리

기존 컨벤션 유지: `AppException`(notFound) + `GlobalExceptionHandler` → `ApiResponse` 포맷.

## 테스트

- `DashboardServiceTest`, `DashboardControllerTest`: 정상 조회(3개 지표 direction/delta/gap 부호 처리, gaugeValue 매핑, points 최대 3개, quarter 포맷), 가게 미등록 404, 상권 데이터 없음 404, 데이터가 1개 분기뿐일 때 `grade.previous == null`이고 `points`가 1개만 반환되는 경우.
