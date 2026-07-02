# 마이페이지 화면 API 구현 설계

## 배경

노션 "API명세서 V2" 기준 마이페이지 화면의 API 4개를 구현한다:

- `GET /api/v1/users/me` — 내 정보·가게 정보 조회 (기존 엔드포인트 확장)
- `PATCH /api/v1/users/me` — 내 정보·가게 정보 수정 (신규)
- `GET /api/v1/users/me/notification-settings` — 알림 설정 조회 (신규)
- `PATCH /api/v1/users/me/notification-settings` — 알림 설정 변경 (신규)

`User` 엔티티에 이미 `smsAlert`/`autoReport`/`urgentAlert`(기본값 false)와 store 관련 필드(`storeRegion`/`categoryCode`/`storeLat`/`storeLng`/`storeName`/`storeOpenDate`)가 모두 존재하므로, 이번 작업은 **마이그레이션 없이** 서비스/컨트롤러/DTO 계층만 추가·확장한다.

## 알려진 스펙 이슈 및 결정 사항

1. **`GET /api/v1/users/me`에 `store` 포함 여부**: 노션 스펙에 미해결 코멘트로 남아있던 질문. → 노션 스펙 예시 그대로 **`store`를 `GET /users/me` 응답에 합친다** (별도 `GET /users/me/store` 신설 안 함). 현재 `UserResponse{id,name,isOnboarded}`에 `store` 필드를 추가하는 확장이며, `UserResponse`는 `UserController`/`UserService`/`UserControllerTest` 외 다른 곳에서 참조되지 않아 영향 범위가 좁다.
2. **`PATCH /api/v1/users/me`와 기존 `PUT /api/v1/users/me/store`(온보딩 플랜에서 이미 구현됨)의 관계**: 두 엔드포인트는 **용도를 분리**한다.
   - `PUT /users/me/store` (기존, 변경 없음): 온보딩 시점의 최초 가게 등록. `regionCode`/`categoryCode`/`lat`/`lng`/`storeName`/`storeOpenDate` 전부 필수, 성공 시 `isOnboarded = true`로 전환.
   - `PATCH /users/me` (신규): 마이페이지에서의 프로필 편집. `name`(선택)과 `store{regionCode,categoryCode,lat,lng}`(선택)만 다루며 `storeName`/`storeOpenDate`/`isOnboarded`는 건드리지 않는다.
3. **`store` 객체는 원자적으로 취급**: `PATCH` 요청에 `store`가 포함되면 `regionCode`/`categoryCode`/`lat`/`lng` 4개 필드가 모두 함께 와야 하는 것으로 간주한다(부분적으로 일부 필드만 있는 `store`는 지원하지 않음). `store` 키 자체가 없으면 가게 정보는 변경하지 않는다.
4. **알림 설정 PATCH의 400 실패 케이스**: 3개 필드 모두 `Boolean`(nullable) 선택값이라 별도 유효성 검증 로직이 필요 없다. 스펙의 400 예시는 잘못된 JSON 형식(타입 불일치 등)에 대한 것으로, 기존 `GlobalExceptionHandler`의 `HttpMessageNotReadableException` 처리로 이미 커버된다.

## 1. `GET /api/v1/users/me` (확장)

- `UserResponse`에 `store` 필드 추가 (레코드 내부에 `StoreInfo` 중첩 레코드: `regionCode`/`regionName`/`categoryCode`/`categoryName`/`lat`/`lng`).
- `user.getStoreRegion() == null`이면 `store: null`.
- `store`가 있을 때 `regionName`/`categoryName`은 기존 `updateStore`와 동일하게 `RegionRepository`/`CategoryRepository`로 조회해 채운다(데이터 정합성이 깨져 조회 실패 시 기존 컨벤션대로 404).
- 인증 실패(401)는 기존 `SecurityConfig`로 이미 처리됨 — 컨트롤러 변경 불필요.

## 2. `PATCH /api/v1/users/me` (신규)

- Request DTO: `UserUpdateRequest(String name, StoreUpdatePartial store)`, `StoreUpdatePartial(String regionCode, String categoryCode, Double lat, Double lng)`. 둘 다 선택.
- `store`가 있으면 `regionCode`/`categoryCode` 존재 검증 — 실패 시 400 `"존재하지 않는 상권코드 또는 업종 코드입니다"` (`PUT /users/me/store`와 동일 메시지 재사용).
- `User`에 신규 도메인 메서드 `updateProfile(String name, String regionCode, String categoryCode, Double lat, Double lng)` 추가: `name`이 null이 아니면 이름 변경, `regionCode`가 null이 아니면 store 4개 필드만 변경(`storeName`/`storeOpenDate`/`isOnboarded`는 그대로 유지).
- 응답은 `GET /users/me`와 동일한 `UserResponse`.

## 3. `GET /api/v1/users/me/notification-settings` (신규)

- `NotificationSettingsResponse(boolean smsAlert, boolean autoReport, boolean urgentAlert)` — `User` 엔티티 값 그대로 반환.

## 4. `PATCH /api/v1/users/me/notification-settings` (신규)

- Request DTO: `NotificationSettingsUpdateRequest(Boolean smsAlert, Boolean autoReport, Boolean urgentAlert)` — 3개 모두 선택(null이면 변경 없음).
- `User`에 신규 도메인 메서드 `updateNotificationSettings(Boolean smsAlert, Boolean autoReport, Boolean urgentAlert)` 추가: null이 아닌 필드만 반영.
- 응답은 변경 후 전체 설정(`NotificationSettingsResponse`, GET과 동일 구조).

## 컨트롤러/서비스 구조

4개 엔드포인트 모두 기존 `UserController`(`/api/v1/users`)와 `UserService`에 메서드를 추가한다. 새 컨트롤러/서비스 클래스를 만들지 않는다 — 전부 `/api/v1/users` 하위 리소스이며 (리포트 화면의 `/reportsHistory`처럼) 경로가 갈라지는 지점이 없기 때문이다.

## 에러 처리

기존 컨벤션 유지: `AppException`(badRequest/notFound) + `GlobalExceptionHandler` → `ApiResponse` 포맷. 401은 `SecurityConfig`가 이미 처리.

## 테스트

- `UserServiceTest`: `getMe`에 store 포함/미포함 케이스 추가, `updateProfile`(name만/store만/둘 다/둘 다 없음/잘못된 regionCode·categoryCode 400) 케이스, `getNotificationSettings`, `updateNotificationSettings`(부분 업데이트 포함) 케이스 추가
- `UserControllerTest`: 기존 `getMe_withValidRequest_returnsOk`를 store 포함 응답으로 갱신, `updateProfile`/`getNotificationSettings`/`updateNotificationSettings` 컨트롤러 테스트 추가 (기존 `@Import(SecurityConfig.class)` + `authentication(...)` 패턴 재사용)
