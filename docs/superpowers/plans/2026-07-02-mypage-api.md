# 마이페이지 화면 API Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 마이페이지 화면의 API 4개(내 정보·가게 정보 조회/수정, 알림 설정 조회/변경)를 구현한다.

**Architecture:** 4개 엔드포인트 모두 기존 `UserController`(`/api/v1/users`)와 `UserService`에 메서드를 추가한다. `User` 엔티티에 store·알림설정 필드가 이미 있으므로 마이그레이션은 없다. `GET /api/v1/users/me`의 기존 `UserResponse{id,name,isOnboarded}`에 `store` 중첩 객체를 추가하고, 이 확장된 응답 빌더를 신규 `PATCH /api/v1/users/me`도 재사용한다.

**Tech Stack:** Spring Boot 4.1, Spring Data JPA, JUnit5 + Mockito + AssertJ + MockMvc, Spring Security Test.

## Global Constraints

- 모든 신규 에러는 기존 `AppException`(badRequest/notFound) + `GlobalExceptionHandler` 컨벤션을 따른다.
- 모든 신규 응답은 `ApiResponse.ok(message, data)` 포맷을 따른다.
- 신규 엔드포인트는 `SecurityConfig`의 `anyRequest().authenticated()`에 자동 포함되므로 `SecurityConfig` 변경은 불필요하다.
- `@AuthenticationPrincipal`을 사용하는 컨트롤러 테스트는 `@AutoConfigureMockMvc(addFilters = false)`를 쓰지 않고 `@Import(SecurityConfig.class)` + 기본 `@AutoConfigureMockMvc` + `SecurityMockMvcRequestPostProcessors.authentication(...)`을 사용한다 (기존 `UserControllerTest`가 이미 이 패턴).
- 기존 `PUT /api/v1/users/me/store`(온보딩용)는 이번 플랜에서 변경하지 않는다. 신규 `PATCH /api/v1/users/me`는 `storeName`/`storeOpenDate`/`isOnboarded`를 건드리지 않는다.
- `PATCH /api/v1/users/me`의 `store` 객체는 원자적으로 취급한다 — `store`가 요청에 있으면 `regionCode`/`categoryCode`/`lat`/`lng` 4개가 모두 있는 것으로 간주한다.
- 알림 설정 3개 필드(`smsAlert`/`autoReport`/`urgentAlert`)는 모두 선택(nullable `Boolean`)이며, 요청에 없는 필드는 변경하지 않는다.

---

### Task 1: `GET /api/v1/users/me` 응답에 `store` 추가

**Files:**
- Modify: `src/main/java/bigbang/butilkka_be/user/dto/UserResponse.java`
- Modify: `src/main/java/bigbang/butilkka_be/user/UserService.java`
- Test: `src/test/java/bigbang/butilkka_be/user/UserServiceTest.java`
- Test: `src/test/java/bigbang/butilkka_be/user/UserControllerTest.java`

**Interfaces:**
- Produces: `UserResponse(Long id, String name, boolean isOnboarded, UserResponse.StoreInfo store)`, `UserResponse.StoreInfo(String regionCode, String regionName, String categoryCode, String categoryName, Double lat, Double lng)` — Task 2에서 그대로 재사용.
- Produces: `UserService.buildStoreInfo(User user): UserResponse.StoreInfo` (private, Task 2에서 재사용하기 위해 이름과 시그니처를 그대로 유지한다)

- [ ] **Step 1: 실패하는 서비스 테스트 작성**

`src/test/java/bigbang/butilkka_be/user/UserServiceTest.java`의 기존 `import`문 아래, 첫 `@Test` 메서드(`updateStore_withValidCodes_updatesUserAndReturnsMergedResponse`) 바로 앞에 아래 2개 테스트를 추가한다:

```java
    @Test
    void getMe_withStoreRegistered_includesStoreInfo() {
        User user = User.create(1L, "김민수");
        user.updateStore("1168064000", "CS100001", 37.5, 127.03, "민수네 한식당", LocalDate.of(2022, 3, 15));
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        Region region = mock(Region.class);
        when(region.getRegionName()).thenReturn("역삼1동");
        Category category = mock(Category.class);
        when(category.getCategoryName()).thenReturn("한식음식점");
        when(regionRepository.findById("1168064000")).thenReturn(Optional.of(region));
        when(categoryRepository.findById("CS100001")).thenReturn(Optional.of(category));

        UserResponse response = userService.getMe(1L);

        assertThat(response.store()).isNotNull();
        assertThat(response.store().regionCode()).isEqualTo("1168064000");
        assertThat(response.store().regionName()).isEqualTo("역삼1동");
        assertThat(response.store().categoryName()).isEqualTo("한식음식점");
    }

    @Test
    void getMe_withoutStore_returnsNullStore() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(User.create(1L, "김민수")));

        UserResponse response = userService.getMe(1L);

        assertThat(response.store()).isNull();
    }

```

파일 맨 위 `import` 블록에 아래 줄이 없으면 추가한다 (기존 `UserServiceTest`는 `StoreResponse`/`StoreUpdateRequest`만 import하고 있어 `UserResponse` import가 없다):

```java
import bigbang.butilkka_be.user.dto.UserResponse;
```

- [ ] **Step 2: 테스트가 실패하는지 확인**

Run: `./gradlew test --tests "bigbang.butilkka_be.user.UserServiceTest" --console=plain`
Expected: FAIL — 컴파일 에러 `cannot find symbol: method store()` (`UserResponse`에 `store` 필드가 없음)

- [ ] **Step 3: `UserResponse`에 `store` 추가**

`src/main/java/bigbang/butilkka_be/user/dto/UserResponse.java` 전체를 아래로 교체:

```java
package bigbang.butilkka_be.user.dto;

import bigbang.butilkka_be.user.User;

public record UserResponse(
        Long id,
        String name,
        boolean isOnboarded,
        StoreInfo store
) {
    public record StoreInfo(
            String regionCode,
            String regionName,
            String categoryCode,
            String categoryName,
            Double lat,
            Double lng
    ) {}

    public static UserResponse of(User user, StoreInfo store) {
        return new UserResponse(user.getId(), user.getName(), user.isOnboarded(), store);
    }
}
```

- [ ] **Step 4: `UserService.getMe`가 store를 채우도록 수정**

`src/main/java/bigbang/butilkka_be/user/UserService.java`의 `getMe` 메서드를 아래로 교체하고, 클래스 마지막(닫는 `}` 앞)에 `buildStoreInfo` private 메서드를 추가한다:

```java
    public UserResponse getMe(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> AppException.notFound("사용자를 찾을 수 없습니다"));
        return UserResponse.of(user, buildStoreInfo(user));
    }
```

```java
    private UserResponse.StoreInfo buildStoreInfo(User user) {
        if (user.getStoreRegion() == null) {
            return null;
        }
        Region region = regionRepository.findById(user.getStoreRegion())
                .orElseThrow(() -> AppException.notFound("존재하지 않는 상권코드입니다"));
        Category category = categoryRepository.findById(user.getCategoryCode())
                .orElseThrow(() -> AppException.notFound("존재하지 않는 업종코드입니다"));
        return new UserResponse.StoreInfo(
                user.getStoreRegion(), region.getRegionName(),
                user.getCategoryCode(), category.getCategoryName(),
                user.getStoreLat(), user.getStoreLng());
    }
```

- [ ] **Step 5: 서비스 테스트 통과 확인**

Run: `./gradlew test --tests "bigbang.butilkka_be.user.UserServiceTest" --console=plain`
Expected: FAIL — `UserControllerTest`는 아직 안 건드렸으므로 이 시점엔 `UserServiceTest`만 실행하면 `BUILD SUCCESSFUL`이어야 한다. 만약 전체 `test` 태스크를 돌리면 `UserControllerTest`가 컴파일 에러(3-arg `UserResponse` 생성자 없음)로 실패한다 — 이는 Step 6에서 고친다.

- [ ] **Step 6: `UserControllerTest`의 기존 테스트를 새 생성자에 맞게 수정**

`src/test/java/bigbang/butilkka_be/user/UserControllerTest.java`의 `getMe_withValidRequest_returnsOk` 테스트를 아래로 교체:

```java
    @Test
    void getMe_withValidRequest_returnsOk() throws Exception {
        when(userService.getMe(eq(1L)))
                .thenReturn(new UserResponse(1L, "김민수", false, null));

        mockMvc.perform(get("/api/v1/users/me")
                        .with(authentication(authAs("1")))
                        .contentType("application/json"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("김민수"))
                .andExpect(jsonPath("$.data.store").doesNotExist());
    }

    @Test
    void getMe_withStore_returnsStoreInfo() throws Exception {
        when(userService.getMe(eq(1L)))
                .thenReturn(new UserResponse(1L, "김민수", true,
                        new UserResponse.StoreInfo("1168064000", "역삼1동", "CS100001", "한식음식점", 37.5, 127.03)));

        mockMvc.perform(get("/api/v1/users/me")
                        .with(authentication(authAs("1")))
                        .contentType("application/json"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.store.regionName").value("역삼1동"));
    }
```

- [ ] **Step 7: 전체 테스트 통과 확인**

Run: `./gradlew test --tests "bigbang.butilkka_be.user.*" --console=plain`
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 8: 커밋**

```bash
git add src/main/java/bigbang/butilkka_be/user src/test/java/bigbang/butilkka_be/user
git commit -m "Add store info to GET /api/v1/users/me response"
```

---

### Task 2: `PATCH /api/v1/users/me` — 프로필 부분 수정

**Files:**
- Create: `src/main/java/bigbang/butilkka_be/user/dto/UserUpdateRequest.java`
- Modify: `src/main/java/bigbang/butilkka_be/user/User.java`
- Modify: `src/main/java/bigbang/butilkka_be/user/UserService.java`
- Modify: `src/main/java/bigbang/butilkka_be/user/UserController.java`
- Test: `src/test/java/bigbang/butilkka_be/user/UserServiceTest.java`
- Test: `src/test/java/bigbang/butilkka_be/user/UserControllerTest.java`

**Interfaces:**
- Consumes: `UserResponse.of(User, UserResponse.StoreInfo)`, `UserService.buildStoreInfo(User)` (Task 1)
- Produces: `UserService.updateProfile(Long userId, UserUpdateRequest request): UserResponse`

- [ ] **Step 1: DTO 작성**

`src/main/java/bigbang/butilkka_be/user/dto/UserUpdateRequest.java`:

```java
package bigbang.butilkka_be.user.dto;

public record UserUpdateRequest(
        String name,
        StoreUpdatePartial store
) {
    public record StoreUpdatePartial(
            String regionCode,
            String categoryCode,
            Double lat,
            Double lng
    ) {}
}
```

- [ ] **Step 2: 실패하는 서비스 테스트 작성**

`src/test/java/bigbang/butilkka_be/user/UserServiceTest.java`에 아래 5개 테스트를 추가한다 (마지막 `@Test` 메서드 뒤, 클래스 닫는 `}` 앞):

```java

    @Test
    void updateProfile_withNameOnly_updatesNameKeepsStore() {
        User user = User.create(1L, "김민수");
        user.updateStore("1168064000", "CS100001", 37.5, 127.03, "민수네 한식당", LocalDate.of(2022, 3, 15));
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        Region region = mock(Region.class);
        when(region.getRegionName()).thenReturn("역삼1동");
        Category category = mock(Category.class);
        when(category.getCategoryName()).thenReturn("한식음식점");
        when(regionRepository.findById("1168064000")).thenReturn(Optional.of(region));
        when(categoryRepository.findById("CS100001")).thenReturn(Optional.of(category));

        UserResponse response = userService.updateProfile(1L, new UserUpdateRequest("김철수", null));

        assertThat(response.name()).isEqualTo("김철수");
        assertThat(response.store().regionCode()).isEqualTo("1168064000");
        assertThat(user.isOnboarded()).isTrue();
    }

    @Test
    void updateProfile_withStoreOnly_updatesStoreKeepsNameAndOnboardedFlag() {
        User user = User.create(1L, "김민수");
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        Region region = mock(Region.class);
        when(region.getRegionName()).thenReturn("역삼1동");
        Category category = mock(Category.class);
        when(category.getCategoryName()).thenReturn("한식음식점");
        when(regionRepository.findById("1168064000")).thenReturn(Optional.of(region));
        when(categoryRepository.findById("CS100001")).thenReturn(Optional.of(category));

        UserUpdateRequest.StoreUpdatePartial store =
                new UserUpdateRequest.StoreUpdatePartial("1168064000", "CS100001", 37.5, 127.03);

        UserResponse response = userService.updateProfile(1L, new UserUpdateRequest(null, store));

        assertThat(response.name()).isEqualTo("김민수");
        assertThat(response.store().regionName()).isEqualTo("역삼1동");
        assertThat(user.isOnboarded()).isFalse();
    }

    @Test
    void updateProfile_withNoFields_returnsUnchangedState() {
        User user = User.create(1L, "김민수");
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));

        UserResponse response = userService.updateProfile(1L, new UserUpdateRequest(null, null));

        assertThat(response.name()).isEqualTo("김민수");
        assertThat(response.store()).isNull();
    }

    @Test
    void updateProfile_withUnknownRegionCode_throwsBadRequest() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(User.create(1L, "김민수")));
        when(regionRepository.findById("UNKNOWN")).thenReturn(Optional.empty());

        UserUpdateRequest.StoreUpdatePartial store =
                new UserUpdateRequest.StoreUpdatePartial("UNKNOWN", "CS100001", 37.5, 127.03);

        assertThatThrownBy(() -> userService.updateProfile(1L, new UserUpdateRequest(null, store)))
                .isInstanceOf(AppException.class)
                .extracting(e -> ((AppException) e).getHttpStatus())
                .isEqualTo(org.springframework.http.HttpStatus.BAD_REQUEST);
    }

    @Test
    void updateProfile_withUnknownCategoryCode_throwsBadRequest() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(User.create(1L, "김민수")));
        when(regionRepository.findById("1168064000")).thenReturn(Optional.of(mock(Region.class)));
        when(categoryRepository.findById("UNKNOWN")).thenReturn(Optional.empty());

        UserUpdateRequest.StoreUpdatePartial store =
                new UserUpdateRequest.StoreUpdatePartial("1168064000", "UNKNOWN", 37.5, 127.03);

        assertThatThrownBy(() -> userService.updateProfile(1L, new UserUpdateRequest(null, store)))
                .isInstanceOf(AppException.class)
                .extracting(e -> ((AppException) e).getHttpStatus())
                .isEqualTo(org.springframework.http.HttpStatus.BAD_REQUEST);
    }
```

파일 상단 import에 다음이 없으면 추가한다: `import bigbang.butilkka_be.user.dto.UserUpdateRequest;`

- [ ] **Step 3: 테스트가 실패하는지 확인**

Run: `./gradlew test --tests "bigbang.butilkka_be.user.UserServiceTest" --console=plain`
Expected: FAIL — `cannot find symbol: method updateProfile` (`UserService`에 아직 없음)

- [ ] **Step 4: `User`에 `updateProfile` 도메인 메서드 추가**

`src/main/java/bigbang/butilkka_be/user/User.java`의 `updateStore` 메서드 뒤(클래스 닫는 `}` 앞)에 추가:

```java

    public void updateProfile(String name, String regionCode, String categoryCode, Double lat, Double lng) {
        if (name != null) {
            this.name = name;
        }
        if (regionCode != null) {
            this.storeRegion = regionCode;
            this.categoryCode = categoryCode;
            this.storeLat = lat;
            this.storeLng = lng;
        }
    }
```

- [ ] **Step 5: `UserService.updateProfile` 구현**

`src/main/java/bigbang/butilkka_be/user/UserService.java`의 `updateStore` 메서드 뒤에 추가:

```java

    @Transactional
    public UserResponse updateProfile(Long userId, UserUpdateRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> AppException.notFound("사용자를 찾을 수 없습니다"));

        String regionCode = null;
        String categoryCode = null;
        Double lat = null;
        Double lng = null;
        if (request.store() != null) {
            UserUpdateRequest.StoreUpdatePartial store = request.store();
            regionRepository.findById(store.regionCode())
                    .orElseThrow(() -> AppException.badRequest("존재하지 않는 상권코드 또는 업종 코드입니다"));
            categoryRepository.findById(store.categoryCode())
                    .orElseThrow(() -> AppException.badRequest("존재하지 않는 상권코드 또는 업종 코드입니다"));
            regionCode = store.regionCode();
            categoryCode = store.categoryCode();
            lat = store.lat();
            lng = store.lng();
        }

        user.updateProfile(request.name(), regionCode, categoryCode, lat, lng);

        return UserResponse.of(user, buildStoreInfo(user));
    }
```

`UserUpdateRequest` import를 파일 상단에 추가한다: `import bigbang.butilkka_be.user.dto.UserUpdateRequest;`

- [ ] **Step 6: 서비스 테스트 통과 확인**

Run: `./gradlew test --tests "bigbang.butilkka_be.user.UserServiceTest" --console=plain`
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 7: 실패하는 컨트롤러 테스트 작성**

`src/test/java/bigbang/butilkka_be/user/UserControllerTest.java`의 `updateStore_withValidRequest_returnsOk` 테스트 뒤(클래스 닫는 `}` 앞)에 추가:

```java

    @Test
    void updateProfile_withValidRequest_returnsOk() throws Exception {
        when(userService.updateProfile(eq(1L), any()))
                .thenReturn(new UserResponse(1L, "김철수", false, null));

        mockMvc.perform(patch("/api/v1/users/me")
                        .with(authentication(authAs("1")))
                        .contentType("application/json")
                        .content("""
                                {
                                  "name": "김철수"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("김철수"));
    }
```

`import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;`를 파일 상단 import 블록에 추가한다.

- [ ] **Step 8: 테스트가 실패하는지 확인**

Run: `./gradlew test --tests "bigbang.butilkka_be.user.UserControllerTest" --console=plain`
Expected: FAIL — `UserController`에 `/me` PATCH 매핑이 없어 405 (테스트에서 200 기대와 불일치)

- [ ] **Step 9: `UserController`에 엔드포인트 추가**

`src/main/java/bigbang/butilkka_be/user/UserController.java` 전체를 아래로 교체:

```java
package bigbang.butilkka_be.user;

import bigbang.butilkka_be.common.response.ApiResponse;
import bigbang.butilkka_be.user.dto.StoreResponse;
import bigbang.butilkka_be.user.dto.StoreUpdateRequest;
import bigbang.butilkka_be.user.dto.UserResponse;
import bigbang.butilkka_be.user.dto.UserUpdateRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @GetMapping("/me")
    public ResponseEntity<ApiResponse<UserResponse>> getMe(
            @AuthenticationPrincipal String userId) {
        UserResponse response = userService.getMe(Long.parseLong(userId));
        return ResponseEntity.ok(ApiResponse.ok("사용자 정보 조회 성공", response));
    }

    @PatchMapping("/me")
    public ResponseEntity<ApiResponse<UserResponse>> updateProfile(
            @AuthenticationPrincipal String userId,
            @RequestBody UserUpdateRequest request) {
        UserResponse response = userService.updateProfile(Long.parseLong(userId), request);
        return ResponseEntity.ok(ApiResponse.ok("사용자 정보 수정 성공", response));
    }

    @PutMapping("/me/store")
    public ResponseEntity<ApiResponse<StoreResponse>> updateStore(
            @AuthenticationPrincipal String userId,
            @RequestBody StoreUpdateRequest request) {
        StoreResponse response = userService.updateStore(Long.parseLong(userId), request);
        return ResponseEntity.ok(ApiResponse.ok("가게 정보 저장 성공", response));
    }
}
```

- [ ] **Step 10: 테스트 통과 확인**

Run: `./gradlew test --tests "bigbang.butilkka_be.user.*" --console=plain`
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 11: 커밋**

```bash
git add src/main/java/bigbang/butilkka_be/user src/test/java/bigbang/butilkka_be/user
git commit -m "Add PATCH /api/v1/users/me profile update endpoint"
```

---

### Task 3: `GET`/`PATCH /api/v1/users/me/notification-settings`

**Files:**
- Create: `src/main/java/bigbang/butilkka_be/user/dto/NotificationSettingsResponse.java`
- Create: `src/main/java/bigbang/butilkka_be/user/dto/NotificationSettingsUpdateRequest.java`
- Modify: `src/main/java/bigbang/butilkka_be/user/User.java`
- Modify: `src/main/java/bigbang/butilkka_be/user/UserService.java`
- Modify: `src/main/java/bigbang/butilkka_be/user/UserController.java`
- Test: `src/test/java/bigbang/butilkka_be/user/UserServiceTest.java`
- Test: `src/test/java/bigbang/butilkka_be/user/UserControllerTest.java`

**Interfaces:**
- Produces: `UserService.getNotificationSettings(Long userId): NotificationSettingsResponse`, `UserService.updateNotificationSettings(Long userId, NotificationSettingsUpdateRequest request): NotificationSettingsResponse` — 이 태스크로 완결.

- [ ] **Step 1: DTO 작성**

`src/main/java/bigbang/butilkka_be/user/dto/NotificationSettingsResponse.java`:

```java
package bigbang.butilkka_be.user.dto;

import bigbang.butilkka_be.user.User;

public record NotificationSettingsResponse(
        boolean smsAlert,
        boolean autoReport,
        boolean urgentAlert
) {
    public static NotificationSettingsResponse from(User user) {
        return new NotificationSettingsResponse(user.isSmsAlert(), user.isAutoReport(), user.isUrgentAlert());
    }
}
```

`src/main/java/bigbang/butilkka_be/user/dto/NotificationSettingsUpdateRequest.java`:

```java
package bigbang.butilkka_be.user.dto;

public record NotificationSettingsUpdateRequest(
        Boolean smsAlert,
        Boolean autoReport,
        Boolean urgentAlert
) {}
```

- [ ] **Step 2: 실패하는 서비스 테스트 작성**

`src/test/java/bigbang/butilkka_be/user/UserServiceTest.java`에 아래 2개 테스트를 추가한다 (클래스 닫는 `}` 앞):

```java

    @Test
    void getNotificationSettings_returnsCurrentSettings() {
        User user = User.create(1L, "김민수");
        user.updateNotificationSettings(true, true, false);
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));

        NotificationSettingsResponse response = userService.getNotificationSettings(1L);

        assertThat(response.smsAlert()).isTrue();
        assertThat(response.autoReport()).isTrue();
        assertThat(response.urgentAlert()).isFalse();
    }

    @Test
    void updateNotificationSettings_withPartialFields_onlyChangesGivenFields() {
        User user = User.create(1L, "김민수");
        user.updateNotificationSettings(true, true, false);
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));

        NotificationSettingsResponse response = userService.updateNotificationSettings(
                1L, new NotificationSettingsUpdateRequest(null, null, true));

        assertThat(response.smsAlert()).isTrue();
        assertThat(response.autoReport()).isTrue();
        assertThat(response.urgentAlert()).isTrue();
    }
```

파일 상단 import 블록에 다음 2줄을 추가한다:

```java
import bigbang.butilkka_be.user.dto.NotificationSettingsResponse;
import bigbang.butilkka_be.user.dto.NotificationSettingsUpdateRequest;
```

- [ ] **Step 3: 테스트가 실패하는지 확인**

Run: `./gradlew test --tests "bigbang.butilkka_be.user.UserServiceTest" --console=plain`
Expected: FAIL — `cannot find symbol: method updateNotificationSettings` (`User`/`UserService`에 아직 없음)

- [ ] **Step 4: `User`에 `updateNotificationSettings` 도메인 메서드 추가**

`src/main/java/bigbang/butilkka_be/user/User.java`의 `updateProfile` 메서드 뒤(클래스 닫는 `}` 앞)에 추가:

```java

    public void updateNotificationSettings(Boolean smsAlert, Boolean autoReport, Boolean urgentAlert) {
        if (smsAlert != null) {
            this.smsAlert = smsAlert;
        }
        if (autoReport != null) {
            this.autoReport = autoReport;
        }
        if (urgentAlert != null) {
            this.urgentAlert = urgentAlert;
        }
    }
```

- [ ] **Step 5: `UserService`에 두 메서드 구현**

`src/main/java/bigbang/butilkka_be/user/UserService.java`의 `updateProfile` 메서드 뒤에 추가:

```java

    public NotificationSettingsResponse getNotificationSettings(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> AppException.notFound("사용자를 찾을 수 없습니다"));
        return NotificationSettingsResponse.from(user);
    }

    @Transactional
    public NotificationSettingsResponse updateNotificationSettings(Long userId, NotificationSettingsUpdateRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> AppException.notFound("사용자를 찾을 수 없습니다"));
        user.updateNotificationSettings(request.smsAlert(), request.autoReport(), request.urgentAlert());
        return NotificationSettingsResponse.from(user);
    }
```

파일 상단 import 블록에 다음 2줄을 추가한다:

```java
import bigbang.butilkka_be.user.dto.NotificationSettingsResponse;
import bigbang.butilkka_be.user.dto.NotificationSettingsUpdateRequest;
```

- [ ] **Step 6: 서비스 테스트 통과 확인**

Run: `./gradlew test --tests "bigbang.butilkka_be.user.UserServiceTest" --console=plain`
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 7: 실패하는 컨트롤러 테스트 작성**

`src/test/java/bigbang/butilkka_be/user/UserControllerTest.java`의 `updateProfile_withValidRequest_returnsOk` 테스트 뒤(클래스 닫는 `}` 앞)에 추가:

```java

    @Test
    void getNotificationSettings_returnsOk() throws Exception {
        when(userService.getNotificationSettings(eq(1L)))
                .thenReturn(new NotificationSettingsResponse(true, true, false));

        mockMvc.perform(get("/api/v1/users/me/notification-settings")
                        .with(authentication(authAs("1"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.smsAlert").value(true))
                .andExpect(jsonPath("$.data.urgentAlert").value(false));
    }

    @Test
    void updateNotificationSettings_withValidRequest_returnsOk() throws Exception {
        when(userService.updateNotificationSettings(eq(1L), any()))
                .thenReturn(new NotificationSettingsResponse(true, true, true));

        mockMvc.perform(patch("/api/v1/users/me/notification-settings")
                        .with(authentication(authAs("1")))
                        .contentType("application/json")
                        .content("""
                                {
                                  "urgentAlert": true
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.urgentAlert").value(true));
    }
```

파일 상단 import 블록에 다음 줄을 추가한다: `import bigbang.butilkka_be.user.dto.NotificationSettingsResponse;`

- [ ] **Step 8: 테스트가 실패하는지 확인**

Run: `./gradlew test --tests "bigbang.butilkka_be.user.UserControllerTest" --console=plain`
Expected: FAIL — `UserController`에 `/me/notification-settings` 매핑이 없어 404/405

- [ ] **Step 9: `UserController`에 엔드포인트 추가**

`src/main/java/bigbang/butilkka_be/user/UserController.java`의 `updateStore` 메서드 뒤(클래스 닫는 `}` 앞)에 추가:

```java

    @GetMapping("/me/notification-settings")
    public ResponseEntity<ApiResponse<NotificationSettingsResponse>> getNotificationSettings(
            @AuthenticationPrincipal String userId) {
        NotificationSettingsResponse response = userService.getNotificationSettings(Long.parseLong(userId));
        return ResponseEntity.ok(ApiResponse.ok("알림 설정 조회 성공", response));
    }

    @PatchMapping("/me/notification-settings")
    public ResponseEntity<ApiResponse<NotificationSettingsResponse>> updateNotificationSettings(
            @AuthenticationPrincipal String userId,
            @RequestBody NotificationSettingsUpdateRequest request) {
        NotificationSettingsResponse response = userService.updateNotificationSettings(Long.parseLong(userId), request);
        return ResponseEntity.ok(ApiResponse.ok("알림 설정 변경 성공", response));
    }
```

파일 상단 import 블록에 다음 2줄을 추가한다:

```java
import bigbang.butilkka_be.user.dto.NotificationSettingsResponse;
import bigbang.butilkka_be.user.dto.NotificationSettingsUpdateRequest;
```

- [ ] **Step 10: 테스트 통과 확인**

Run: `./gradlew test --tests "bigbang.butilkka_be.user.*" --console=plain`
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 11: 커밋**

```bash
git add src/main/java/bigbang/butilkka_be/user src/test/java/bigbang/butilkka_be/user
git commit -m "Add GET/PATCH /api/v1/users/me/notification-settings endpoints"
```

---

### Task 4: 전체 검증

**Files:** 없음 (기존 파일 재확인만 수행)

- [ ] **Step 1: 전체 테스트 스위트 실행**

Run: `./gradlew test --console=plain`
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 2: 앱 실행 후 인증 없이 4개 엔드포인트가 401 또는 403을 반환하는지 확인**

Run: `./gradlew bootRun --console=plain` (백그라운드 실행, MySQL 컨테이너가 떠 있어야 함)

Run: `curl -s -o /dev/null -w "%{http_code}\n" "http://localhost:8080/api/v1/users/me"`
Expected: `401` 또는 `403` (Spring Security 기본 익명 거부 동작 — 맵/상권 플랜 Task 9에서 이미 확인된 것과 동일)

Run: `curl -s -o /dev/null -w "%{http_code}\n" -X PATCH "http://localhost:8080/api/v1/users/me" -H "Content-Type: application/json" -d '{}'`
Expected: `401` 또는 `403`

Run: `curl -s -o /dev/null -w "%{http_code}\n" "http://localhost:8080/api/v1/users/me/notification-settings"`
Expected: `401` 또는 `403`

Run: `curl -s -o /dev/null -w "%{http_code}\n" -X PATCH "http://localhost:8080/api/v1/users/me/notification-settings" -H "Content-Type: application/json" -d '{}'`
Expected: `401` 또는 `403`

- [ ] **Step 3: 앱 종료**

- [ ] **Step 4: 최종 상태 확인**

Run: `git log --oneline -6`
Expected: Task 1~3의 커밋(store 추가, PATCH /users/me, notification-settings)이 순서대로 보임
