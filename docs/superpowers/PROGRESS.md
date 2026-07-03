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
| 알림 | 2/2 | 완료, push 완료 (설계: `docs/superpowers/specs/2026-07-03-notification-api-design.md`, 플랜: `docs/superpowers/plans/2026-07-03-notification-api.md`, Task 1~3, 커밋 `747c77f..fb36d5b`, subagent-driven-development로 진행. Task별 리뷰 전부 Approved, 최종 whole-branch 리뷰(Opus) Ready to merge - 수정 없이 그대로 merge 가능 판정) |
| 홈 | 1/1 | 완료, push 완료 (설계: `docs/superpowers/specs/2026-07-03-dashboard-api-design.md`, 플랜: `docs/superpowers/plans/2026-07-03-dashboard-api.md`, Task 1~2, 커밋 `0f4aa27`, subagent-driven-development로 진행. `Report`에 의존하지 않고 기존 `CommercialStats`만 재사용하는 방향으로 설계. 리뷰 Approved, 최종 whole-branch 리뷰(Opus) Ready to merge. 후속 검토 권장 사항(비차단): (1) A~E 등급→게이지 매핑이 이 서비스와 리포트 V22 마이그레이션 두 곳에 중복 존재 - 드리프트 위험, (2) 홈 화면 등급(`CommercialStats` 기준)과 리포트 화면 등급(`reports` 테이블 기준)이 같은 상권/분기라도 서로 다를 수 있음, (3) 시드 데이터가 약 15개 상권에만 있어 실제 유저 대부분은 404를 보게 됨 - 커버리지 확대나 빈 상태 UI 후속 필요) |

## 전체 27개 API 구현 완료

인증 → 온보딩 → 지도/상권 → 마이페이지 → 리포트 → 알림 → 홈, 계획된 모든 화면의 백엔드 구현이 끝났다.
