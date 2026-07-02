# API 구현 진행 상황

Notion "API명세서 V2" (https://app.notion.com/p/46d6004f3133832ba3e18185e637ed33) 기준 전체 27개 API의 백엔드 구현 현황. Notion의 "백엔드 진행여부" 컬럼은 갱신되어 있지 않으므로 이 문서를 기준으로 삼는다.

## 완료된 화면

| 화면 | API 수 | 상태 |
|---|---|---|
| 인증 | 4/4 | 완료 (기존 구현) |
| 온보딩 | 3/3 | 완료 (`docs/superpowers/plans/2026-07-02-onboarding-api-region-migration.md`, Task 1~9) |
| 지도/상권 | 7/7 | 완료 (`docs/superpowers/plans/2026-07-02-map-district-api.md`, Task 1~9) |
| 마이페이지 | 4/4 | 완료, push 완료 (`docs/superpowers/plans/2026-07-02-mypage-api.md`, Task 1~4, 커밋 `da61339..9246eae`) |

## 진행 중: 리포트 화면 (4개 API)

- 설계: `docs/superpowers/specs/2026-07-02-report-api-design.md` (승인 완료)
- 플랜: `docs/superpowers/plans/2026-07-02-report-api.md` (Task 1~5)
- 진행 상태:
  - **Task 1 (V22 마이그레이션: reports.year/score, report_cause.description, report_alternative_regions.stat 추가): 구현 완료, 로컬 커밋 `ff19cf7`, 아직 리뷰 전, 아직 push 안 됨**
  - Task 2 (`GET /reports/latest`, `GET /reports/{reportId}`): 미착수
  - Task 3 (`GET /reportsHistory`): 미착수
  - Task 4 (`GET /reports/{reportId}/cases`): 미착수
  - Task 5 (전체 검증): 미착수

### 다음에 할 일 (재개 시)
1. Task 1 리뷰 진행 (review-package 생성 → 리뷰어 디스패치 → 원장(`​.superpowers/sdd/progress-report.md`, git-ignored) 갱신)
2. Task 2~5를 subagent-driven-development로 순서대로 진행
3. 리포트 완료 후 push

## 아직 설계도 안 한 화면

- **알림** (2개 API: `GET /api/v1/notifications`, `PATCH /api/v1/notifications/{notificationId}`) — `Notification` 엔티티/리포지토리만 존재, 서비스/컨트롤러 없음
- **홈** (1개 API: `GET /api/v1/dashboard`) — 엔티티조차 없음. `store` + `grade`(현재/이전/게이지) + `briefing` + 최근 3분기 유동인구/점포수/폐업률 추이를 합친 응답이라, 기존 `CommercialStatsQueryService`(지도/상권)와 `Report`(리포트)를 재사용할 가능성이 커서 브레인스토밍부터 필요

합의된 순서: **리포트 → 알림 → 홈**
