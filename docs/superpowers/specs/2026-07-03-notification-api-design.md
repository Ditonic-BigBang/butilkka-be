# 알림 화면 API 구현 설계

## 배경

노션 "API명세서 V2" 기준 알림 화면의 API 2개를 구현한다:

- `GET /api/v1/notifications` — 받은 알림 목록
- `PATCH /api/v1/notifications/{notificationId}` — 알림 읽음 처리

알림 도메인은 `Notification`/`NotificationCategory`/`NotificationRepository`가 이미 존재한다(`findByUserIdOrderBySentAtDesc` 포함). 이번 작업은 이 기존 데이터를 조회/갱신하는 서비스·컨트롤러 계층을 추가하는 것이며, 리포트 화면 때와 달리 스키마 변경이나 스펙 모순은 없다.

## 알려진 스펙 이슈 및 결정 사항

1. **`sentAt` 직렬화 포맷**: 스펙 예시는 `"2026-06-20T09:00:00+09:00"`(UTC+9 오프셋 포함)이지만, `Notification.sentAt`은 `LocalDateTime`이고 이 프로젝트는 지금까지 응답에 시각 필드를 노출한 적이 없어 커스텀 Jackson 설정도 없다. → Spring Boot 기본 직렬화(오프셋 없는 ISO-8601, 예: `"2026-06-20T09:00:00"`)를 그대로 사용한다. DB가 이미 Asia/Seoul 기준으로 저장되므로 값 자체는 정확하고, 이번 범위에서 커스텀 시리얼라이저는 추가하지 않는다.
2. **읽음 처리 멱등성**: 스펙에 이미 읽은 알림을 다시 읽음 처리할 때의 동작이 명시되어 있지 않다. → 멱등하게 처리한다(이미 `isRead=true`여도 에러 없이 200과 `isRead: true`를 반환).
3. **다른 유저의 `notificationId` 접근 시 처리**: 스펙의 실패 예시가 이미 404 `"존재하지 않는 알림입니다."`이므로, 리포트 화면과 동일한 컨벤션으로 존재하지 않는 경우와 다른 유저 소유인 경우를 구분하지 않고 동일하게 404로 응답한다(존재 여부를 숨김).

## 1. `GET /api/v1/notifications`

- Query: `offset`(기본 0), `limit`(기본 20).
- 인증된 유저의 전체 알림을 `NotificationRepository.findByUserIdOrderBySentAtDesc`로 가져온 뒤 offset/limit을 메모리에서 적용(건수가 적어 DB 페이징 불필요 — `ReportHistoryService`와 동일 스타일).
- 항목: `notificationId`, `category`(enum name 그대로: EMERGENCY/REPORT/SYSTEM), `title`, `content`, `isRead`, `sentAt`.
- `totalCount`(전체 개수), `hasNext`(다음 페이지 존재 여부) 포함.
- `offset`/`limit`이 음수면 `AppException.badRequest`로 400 반환(리포트 화면 최종 리뷰에서 나온 패턴을 처음부터 반영).

## 2. `PATCH /api/v1/notifications/{notificationId}`

- PathVariable `notificationId`가 인증된 유저 소유가 아니거나 존재하지 않으면 404 `"존재하지 않는 알림입니다."`
- 소유자 확인 후 `Notification.markAsRead()` 호출(멱등, 이미 읽음이어도 에러 없음) → dirty checking으로 저장(명시적 `save()` 불필요, `User.updateProfile`과 동일 패턴).
- 응답: `notificationId`, `isRead`(항상 `true`).

## 3. 엔티티 변경

`Notification`에 mutator 추가(기존 `User.updateProfile`/`updateNotificationSettings` 스타일):

```java
public void markAsRead() {
    this.isRead = true;
}
```

스키마/마이그레이션 변경 없음 — `is_read` 컬럼은 이미 존재.

## 4. 컨트롤러/서비스 구조

기존 컨벤션(엔드포인트 그룹당 컨트롤러 하나)을 따른다:

- `NotificationController` (`@RequestMapping("/api/v1/notifications")`): `GET`(목록), `PATCH /{notificationId}`(읽음 처리)
- `NotificationService`: `getNotifications(Long userId, int offset, int limit): NotificationListResponse`, `markAsRead(Long userId, Long notificationId): NotificationReadResponse`

두 엔드포인트 모두 `@AuthenticationPrincipal`이 필요하므로, 컨트롤러 테스트는 `ReportControllerTest`와 동일하게 `@AutoConfigureMockMvc(addFilters = false)` 대신 `@Import(SecurityConfig.class)` + 기본 `@AutoConfigureMockMvc` + `SecurityMockMvcRequestPostProcessors.authentication(...)`을 사용한다.

## 5. DTO

```java
public record NotificationListResponse(
        int totalCount,
        boolean hasNext,
        List<NotificationItem> notifications
) {
    public record NotificationItem(
            Long notificationId, String category, String title, String content,
            boolean isRead, LocalDateTime sentAt) {}
}

public record NotificationReadResponse(Long notificationId, boolean isRead) {}
```

## 에러 처리

기존 컨벤션 유지: `AppException`(notFound/badRequest) + `GlobalExceptionHandler` → `ApiResponse` 포맷.

## 테스트

- `NotificationServiceTest`, `NotificationControllerTest`: 목록 조회(정렬 최신순, offset/limit 적용, totalCount/hasNext, 음수 offset/limit 400), 읽음 처리(성공, 이미 읽음 상태에서 멱등 성공, 다른 유저 소유 404, 존재하지 않는 알림 404).
