# API 구현 진행 상황

Notion "API명세서 V2" (https://app.notion.com/p/46d6004f3133832ba3e18185e637ed33) 기준 전체 27개 API의 백엔드 구현 현황. Notion의 "백엔드 진행여부" 컬럼은 갱신되어 있지 않으므로 이 문서를 기준으로 삼는다.

## 완료된 화면

| 화면 | API 수 | 상태 |
|---|---|---|
| 인증 | 4/4 | 완료 (기존 구현) |
| 온보딩 | 3/3 | 완료 (`docs/superpowers/plans/2026-07-02-onboarding-api-region-migration.md`, Task 1~9) |
| 지도/상권 | 7/7 | 완료 (`docs/superpowers/plans/2026-07-02-map-district-api.md`, Task 1~9) |
| 마이페이지 | 4/4 | 완료, push 완료 (`docs/superpowers/plans/2026-07-02-mypage-api.md`, Task 1~4, 커밋 `da61339..9246eae`) |
| 리포트 | 4/4 | 완료, push 완료 (`docs/superpowers/plans/2026-07-02-report-api.md`, Task 1~5, 커밋 `ff19cf7..3d48764`, subagent-driven-development로 진행. Task별 리뷰 전부 Approved, 최종 whole-branch 리뷰(Opus)에서 나온 Important 2건(offset/limit 음수 처리, similarCase 기간 null 처리)은 `3d48764`에서 수정 완료. Notion "API명세서 V2" 4개 페이지 직접 대조 완료 - 필드/메시지 전부 스펙 일치) |

## 아직 설계도 안 한 화면

- **알림** (2개 API: `GET /api/v1/notifications`, `PATCH /api/v1/notifications/{notificationId}`) — `Notification` 엔티티/리포지토리만 존재, 서비스/컨트롤러 없음
- **홈** (1개 API: `GET /api/v1/dashboard`) — 엔티티조차 없음. `store` + `grade`(현재/이전/게이지) + `briefing` + 최근 3분기 유동인구/점포수/폐업률 추이를 합친 응답이라, 기존 `CommercialStatsQueryService`(지도/상권)와 `Report`(리포트)를 재사용할 가능성이 커서 브레인스토밍부터 필요

합의된 순서: **리포트 → 알림 → 홈**
