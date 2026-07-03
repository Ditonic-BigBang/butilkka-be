# 알림 화면 API Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 알림 화면의 API 2개(받은 알림 목록, 알림 읽음 처리)를 구현한다.

**Architecture:** 이미 존재하는 `Notification`/`NotificationCategory`/`NotificationRepository`를 그대로 사용한다(스키마 변경 없음). `Notification`에 `markAsRead()` mutator를 추가하고, `notification` 패키지에 `NotificationService`와 `NotificationController`를 신규 추가한다.

**Tech Stack:** Spring Boot 4.1, Spring Data JPA, MySQL 8.0 (Docker), JUnit5 + Mockito + AssertJ + MockMvc, Spring Security Test.

## Global Constraints

- 모든 신규 에러는 기존 `AppException`(notFound, badRequest) + `GlobalExceptionHandler` 컨벤션을 따른다.
- 모든 신규 응답은 `ApiResponse.ok(message, data)` 포맷을 따른다.
- 신규 엔드포인트는 `SecurityConfig`의 `anyRequest().authenticated()`에 자동 포함되므로 `SecurityConfig` 변경은 불필요하다.
- `@AuthenticationPrincipal`을 사용하는 컨트롤러 테스트는 `@AutoConfigureMockMvc(addFilters = false)`를 쓰지 않고 `@Import(SecurityConfig.class)` + 기본 `@AutoConfigureMockMvc` + `SecurityMockMvcRequestPostProcessors.authentication(...)`을 사용한다.
- `offset`/`limit`이 음수면 `AppException.badRequest("offset과 limit은 0 이상이어야 합니다.")`로 400을 반환한다 (`ReportHistoryService`와 동일 패턴).
- 다른 유저 소유의 `notificationId`에 접근하거나 존재하지 않는 `notificationId`에 접근하면 동일하게 404 `"존재하지 않는 알림입니다."`를 반환한다 (존재 여부를 숨김).
- 이미 읽음 처리된 알림(`isRead=true`)에 다시 읽음 처리를 요청해도 에러 없이 멱등하게 200과 `isRead: true`를 반환한다.
- `sentAt`은 `LocalDateTime`을 그대로 응답에 노출한다(Spring Boot 기본 직렬화, 오프셋 없는 ISO-8601 — 예: `"2026-06-20T09:00:00"`). 커스텀 시리얼라이저는 추가하지 않는다.

---

### Task 1: `GET /api/v1/notifications`

**Files:**
- Create: `src/main/java/bigbang/butilkka_be/notification/dto/NotificationListResponse.java`
- Create: `src/main/java/bigbang/butilkka_be/notification/NotificationService.java`
- Create: `src/main/java/bigbang/butilkka_be/notification/NotificationController.java`
- Test: `src/test/java/bigbang/butilkka_be/notification/NotificationServiceTest.java`
- Test: `src/test/java/bigbang/butilkka_be/notification/NotificationControllerTest.java`

**Interfaces:**
- Consumes: 기존 `NotificationRepository.findByUserIdOrderBySentAtDesc(Long): List<Notification>`, `Notification.getNotificationId(): Long`, `Notification.getCategory(): NotificationCategory`, `Notification.getTitle(): String`, `Notification.getContent(): String`, `Notification.isRead(): boolean`, `Notification.getSentAt(): LocalDateTime`
- Produces: `NotificationService.getNotifications(Long userId, int offset, int limit): NotificationListResponse`, `NotificationController`(`@RequestMapping("/api/v1/notifications")`) — Task 2에서 같은 `NotificationService`/`NotificationController` 파일에 `markAsRead` 메서드/엔드포인트를 추가할 때 이 파일 구조를 그대로 따른다.

- [ ] **Step 1: DTO 작성**

`src/main/java/bigbang/butilkka_be/notification/dto/NotificationListResponse.java`:

```java
package bigbang.butilkka_be.notification.dto;

import java.time.LocalDateTime;
import java.util.List;

public record NotificationListResponse(
        int totalCount,
        boolean hasNext,
        List<NotificationItem> notifications
) {
    public record NotificationItem(
            Long notificationId,
            String category,
            String title,
            String content,
            boolean isRead,
            LocalDateTime sentAt
    ) {}
}
```

- [ ] **Step 2: 실패하는 서비스 테스트 작성**

`src/test/java/bigbang/butilkka_be/notification/NotificationServiceTest.java`:

```java
package bigbang.butilkka_be.notification;

import bigbang.butilkka_be.common.exception.AppException;
import bigbang.butilkka_be.notification.dto.NotificationListResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    @Mock
    private NotificationRepository notificationRepository;

    private NotificationService service;

    @BeforeEach
    void setUp() {
        service = new NotificationService(notificationRepository);
    }

    private static Notification notificationOf(
            Long id, NotificationCategory category, String title, String content, boolean isRead, LocalDateTime sentAt) {
        Notification notification = mock(Notification.class);
        when(notification.getNotificationId()).thenReturn(id);
        when(notification.getCategory()).thenReturn(category);
        when(notification.getTitle()).thenReturn(title);
        when(notification.getContent()).thenReturn(content);
        when(notification.isRead()).thenReturn(isRead);
        when(notification.getSentAt()).thenReturn(sentAt);
        return notification;
    }

    @Test
    void getNotifications_returnsItemsInRepositoryOrder() {
        LocalDateTime now = LocalDateTime.of(2026, 6, 20, 9, 0);
        Notification n1 = notificationOf(301L, NotificationCategory.EMERGENCY, "제목1", "내용1", false, now);
        Notification n2 = notificationOf(300L, NotificationCategory.SYSTEM, "제목2", "내용2", true, now.minusDays(1));
        when(notificationRepository.findByUserIdOrderBySentAtDesc(1L)).thenReturn(List.of(n1, n2));

        NotificationListResponse response = service.getNotifications(1L, 0, 20);

        assertThat(response.totalCount()).isEqualTo(2);
        assertThat(response.hasNext()).isFalse();
        assertThat(response.notifications()).hasSize(2);
        assertThat(response.notifications().get(0).notificationId()).isEqualTo(301L);
        assertThat(response.notifications().get(0).category()).isEqualTo("EMERGENCY");
        assertThat(response.notifications().get(0).isRead()).isFalse();
        assertThat(response.notifications().get(1).notificationId()).isEqualTo(300L);
        assertThat(response.notifications().get(1).isRead()).isTrue();
    }

    @Test
    void getNotifications_appliesOffsetAndLimit() {
        LocalDateTime now = LocalDateTime.of(2026, 6, 20, 9, 0);
        Notification n1 = notificationOf(4L, NotificationCategory.SYSTEM, "t4", "c4", false, now);
        Notification n2 = notificationOf(3L, NotificationCategory.SYSTEM, "t3", "c3", false, now);
        Notification n3 = notificationOf(2L, NotificationCategory.SYSTEM, "t2", "c2", false, now);
        Notification n4 = notificationOf(1L, NotificationCategory.SYSTEM, "t1", "c1", false, now);
        when(notificationRepository.findByUserIdOrderBySentAtDesc(1L)).thenReturn(List.of(n1, n2, n3, n4));

        NotificationListResponse response = service.getNotifications(1L, 1, 2);

        assertThat(response.totalCount()).isEqualTo(4);
        assertThat(response.hasNext()).isTrue();
        assertThat(response.notifications()).hasSize(2);
        assertThat(response.notifications().get(0).notificationId()).isEqualTo(3L);
        assertThat(response.notifications().get(1).notificationId()).isEqualTo(2L);
    }

    @Test
    void getNotifications_withNoNotifications_returnsEmptyList() {
        when(notificationRepository.findByUserIdOrderBySentAtDesc(1L)).thenReturn(List.of());

        NotificationListResponse response = service.getNotifications(1L, 0, 20);

        assertThat(response.totalCount()).isEqualTo(0);
        assertThat(response.hasNext()).isFalse();
        assertThat(response.notifications()).isEmpty();
    }

    @Test
    void getNotifications_withNegativeOffset_throwsBadRequest() {
        assertThatThrownBy(() -> service.getNotifications(1L, -1, 20))
                .isInstanceOf(AppException.class)
                .extracting(e -> ((AppException) e).getHttpStatus())
                .isEqualTo(org.springframework.http.HttpStatus.BAD_REQUEST);
    }

    @Test
    void getNotifications_withNegativeLimit_throwsBadRequest() {
        assertThatThrownBy(() -> service.getNotifications(1L, 0, -1))
                .isInstanceOf(AppException.class)
                .extracting(e -> ((AppException) e).getHttpStatus())
                .isEqualTo(org.springframework.http.HttpStatus.BAD_REQUEST);
    }
}
```

- [ ] **Step 3: 테스트가 실패하는지 확인**

Run: `./gradlew test --tests "bigbang.butilkka_be.notification.NotificationServiceTest" --console=plain`
Expected: FAIL — `NotificationService` 클래스가 존재하지 않음 (컴파일 에러)

- [ ] **Step 4: `NotificationService` 구현**

`src/main/java/bigbang/butilkka_be/notification/NotificationService.java`:

```java
package bigbang.butilkka_be.notification;

import bigbang.butilkka_be.common.exception.AppException;
import bigbang.butilkka_be.notification.dto.NotificationListResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class NotificationService {

    private final NotificationRepository notificationRepository;

    public NotificationListResponse getNotifications(Long userId, int offset, int limit) {
        if (offset < 0 || limit < 0) {
            throw AppException.badRequest("offset과 limit은 0 이상이어야 합니다.");
        }

        List<Notification> all = notificationRepository.findByUserIdOrderBySentAtDesc(userId);

        List<NotificationListResponse.NotificationItem> page = all.stream()
                .skip(offset)
                .limit(limit)
                .map(this::toItem)
                .toList();

        boolean hasNext = offset + page.size() < all.size();

        return new NotificationListResponse(all.size(), hasNext, page);
    }

    private NotificationListResponse.NotificationItem toItem(Notification notification) {
        return new NotificationListResponse.NotificationItem(
                notification.getNotificationId(),
                notification.getCategory().name(),
                notification.getTitle(),
                notification.getContent(),
                notification.isRead(),
                notification.getSentAt());
    }
}
```

- [ ] **Step 5: 서비스 테스트 통과 확인**

Run: `./gradlew test --tests "bigbang.butilkka_be.notification.NotificationServiceTest" --console=plain`
Expected: `BUILD SUCCESSFUL`, 5개 테스트 모두 PASS

- [ ] **Step 6: 실패하는 컨트롤러 테스트 작성**

`src/test/java/bigbang/butilkka_be/notification/NotificationControllerTest.java`:

```java
package bigbang.butilkka_be.notification;

import bigbang.butilkka_be.common.security.JwtTokenProvider;
import bigbang.butilkka_be.common.security.SecurityConfig;
import bigbang.butilkka_be.notification.dto.NotificationListResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(NotificationController.class)
@AutoConfigureMockMvc
@Import(SecurityConfig.class)
class NotificationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private NotificationService notificationService;

    @MockitoBean
    private JwtTokenProvider jwtTokenProvider;

    private static Authentication authAs(String userId) {
        return new UsernamePasswordAuthenticationToken(userId, null, List.of());
    }

    @Test
    void getNotifications_returnsOk() throws Exception {
        when(notificationService.getNotifications(eq(1L), eq(0), eq(20))).thenReturn(
                new NotificationListResponse(1, false, List.of(
                        new NotificationListResponse.NotificationItem(
                                301L, "EMERGENCY", "제목", "내용", false,
                                LocalDateTime.of(2026, 6, 20, 9, 0)))));

        mockMvc.perform(get("/api/v1/notifications")
                        .with(authentication(authAs("1"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.notifications[0].category").value("EMERGENCY"));
    }

    @Test
    void getNotifications_withOffsetAndLimit_passesParamsThrough() throws Exception {
        when(notificationService.getNotifications(eq(1L), eq(2), eq(5))).thenReturn(
                new NotificationListResponse(10, true, List.of()));

        mockMvc.perform(get("/api/v1/notifications")
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

Run: `./gradlew test --tests "bigbang.butilkka_be.notification.NotificationControllerTest" --console=plain`
Expected: FAIL — `NotificationController` 클래스가 존재하지 않음 (컴파일 에러)

- [ ] **Step 8: `NotificationController` 구현**

`src/main/java/bigbang/butilkka_be/notification/NotificationController.java`:

```java
package bigbang.butilkka_be.notification;

import bigbang.butilkka_be.common.response.ApiResponse;
import bigbang.butilkka_be.notification.dto.NotificationListResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;

    @GetMapping
    public ResponseEntity<ApiResponse<NotificationListResponse>> getNotifications(
            @AuthenticationPrincipal String userId,
            @RequestParam(defaultValue = "0") int offset,
            @RequestParam(defaultValue = "20") int limit) {
        NotificationListResponse response = notificationService.getNotifications(Long.parseLong(userId), offset, limit);
        return ResponseEntity.ok(ApiResponse.ok("알림 목록 조회 성공", response));
    }
}
```

- [ ] **Step 9: 테스트 통과 확인**

Run: `./gradlew test --tests "bigbang.butilkka_be.notification.*" --console=plain`
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 10: 커밋**

```bash
git add src/main/java/bigbang/butilkka_be/notification src/test/java/bigbang/butilkka_be/notification
git commit -m "Add GET /api/v1/notifications endpoint"
```

---

### Task 2: `PATCH /api/v1/notifications/{notificationId}`

**Files:**
- Modify: `src/main/java/bigbang/butilkka_be/notification/Notification.java`
- Create: `src/main/java/bigbang/butilkka_be/notification/dto/NotificationReadResponse.java`
- Modify: `src/main/java/bigbang/butilkka_be/notification/NotificationService.java` (Task 1에서 생성)
- Modify: `src/main/java/bigbang/butilkka_be/notification/NotificationController.java` (Task 1에서 생성)
- Test: Modify `src/test/java/bigbang/butilkka_be/notification/NotificationServiceTest.java` (Task 1에서 생성)
- Test: Modify `src/test/java/bigbang/butilkka_be/notification/NotificationControllerTest.java` (Task 1에서 생성)

**Interfaces:**
- Consumes: 기존 `NotificationRepository.findById(Long): Optional<Notification>` (JpaRepository 상속), `Notification.getUserId(): Long`, Task 1의 `NotificationService`/`NotificationController` 구조
- Produces: `NotificationService.markAsRead(Long userId, Long notificationId): NotificationReadResponse` — 이 태스크로 완결 (다른 태스크가 재사용하지 않음)

- [ ] **Step 1: `Notification`에 mutator 추가 (실패하는 테스트 없이 바로 추가 — 엔티티 메서드는 단위 테스트 대상이 아니라 서비스 테스트로 커버)**

`src/main/java/bigbang/butilkka_be/notification/Notification.java`의 `create(...)` 정적 팩토리 메서드 바로 뒤(클래스 닫는 `}` 앞)에 추가:

```java

    public void markAsRead() {
        this.isRead = true;
    }
```

- [ ] **Step 2: DTO 작성**

`src/main/java/bigbang/butilkka_be/notification/dto/NotificationReadResponse.java`:

```java
package bigbang.butilkka_be.notification.dto;

public record NotificationReadResponse(Long notificationId, boolean isRead) {}
```

- [ ] **Step 3: 실패하는 서비스 테스트 작성**

`src/test/java/bigbang/butilkka_be/notification/NotificationServiceTest.java`의 마지막 `@Test` 메서드 뒤(클래스 닫는 `}` 앞)에 추가하고, 파일 상단 import 목록에 아래 import들을 추가한다:

```java
import bigbang.butilkka_be.notification.dto.NotificationReadResponse;
import java.util.Optional;
import static org.mockito.Mockito.verify;
```

테스트 메서드:

```java

    @Test
    void markAsRead_withOwnedUnreadNotification_marksAndReturnsRead() {
        Notification notification = mock(Notification.class);
        when(notification.getNotificationId()).thenReturn(301L);
        when(notification.getUserId()).thenReturn(1L);
        when(notificationRepository.findById(301L)).thenReturn(Optional.of(notification));

        NotificationReadResponse response = service.markAsRead(1L, 301L);

        verify(notification).markAsRead();
        assertThat(response.notificationId()).isEqualTo(301L);
        assertThat(response.isRead()).isTrue();
    }

    @Test
    void markAsRead_calledTwice_isIdempotent() {
        Notification notification = mock(Notification.class);
        when(notification.getNotificationId()).thenReturn(301L);
        when(notification.getUserId()).thenReturn(1L);
        when(notificationRepository.findById(301L)).thenReturn(Optional.of(notification));

        NotificationReadResponse first = service.markAsRead(1L, 301L);
        NotificationReadResponse second = service.markAsRead(1L, 301L);

        verify(notification, org.mockito.Mockito.times(2)).markAsRead();
        assertThat(first.isRead()).isTrue();
        assertThat(second.isRead()).isTrue();
    }

    @Test
    void markAsRead_withOtherUsersNotification_throwsNotFound() {
        Notification notification = mock(Notification.class);
        when(notification.getUserId()).thenReturn(2L);
        when(notificationRepository.findById(301L)).thenReturn(Optional.of(notification));

        assertThatThrownBy(() -> service.markAsRead(1L, 301L))
                .isInstanceOf(AppException.class)
                .extracting(e -> ((AppException) e).getHttpStatus())
                .isEqualTo(org.springframework.http.HttpStatus.NOT_FOUND);
    }

    @Test
    void markAsRead_withUnknownNotificationId_throwsNotFound() {
        when(notificationRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.markAsRead(1L, 999L))
                .isInstanceOf(AppException.class)
                .extracting(e -> ((AppException) e).getHttpStatus())
                .isEqualTo(org.springframework.http.HttpStatus.NOT_FOUND);
    }
```

- [ ] **Step 4: 테스트가 실패하는지 확인**

Run: `./gradlew test --tests "bigbang.butilkka_be.notification.NotificationServiceTest" --console=plain`
Expected: FAIL — `NotificationService.markAsRead` 메서드가 존재하지 않음, `NotificationReadResponse` 클래스가 존재하지 않음 (컴파일 에러)

- [ ] **Step 5: `NotificationService.markAsRead` 구현**

`src/main/java/bigbang/butilkka_be/notification/NotificationService.java`의 import 목록에 추가:

```java
import bigbang.butilkka_be.notification.dto.NotificationReadResponse;
import org.springframework.transaction.annotation.Transactional;
```

클래스 선언을 `@Transactional(readOnly = true)`로 바꾸고 (`@Service` 바로 아래, `@RequiredArgsConstructor` 아래에 추가), `getNotifications` 메서드 뒤(클래스 닫는 `}` 앞)에 아래 메서드를 추가한다:

```java

    @Transactional
    public NotificationReadResponse markAsRead(Long userId, Long notificationId) {
        Notification notification = notificationRepository.findById(notificationId)
                .filter(n -> n.getUserId().equals(userId))
                .orElseThrow(() -> AppException.notFound("존재하지 않는 알림입니다."));

        notification.markAsRead();

        return new NotificationReadResponse(notification.getNotificationId(), true);
    }
```

전체 파일은 다음과 같아야 한다:

```java
package bigbang.butilkka_be.notification;

import bigbang.butilkka_be.common.exception.AppException;
import bigbang.butilkka_be.notification.dto.NotificationListResponse;
import bigbang.butilkka_be.notification.dto.NotificationReadResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class NotificationService {

    private final NotificationRepository notificationRepository;

    public NotificationListResponse getNotifications(Long userId, int offset, int limit) {
        if (offset < 0 || limit < 0) {
            throw AppException.badRequest("offset과 limit은 0 이상이어야 합니다.");
        }

        List<Notification> all = notificationRepository.findByUserIdOrderBySentAtDesc(userId);

        List<NotificationListResponse.NotificationItem> page = all.stream()
                .skip(offset)
                .limit(limit)
                .map(this::toItem)
                .toList();

        boolean hasNext = offset + page.size() < all.size();

        return new NotificationListResponse(all.size(), hasNext, page);
    }

    @Transactional
    public NotificationReadResponse markAsRead(Long userId, Long notificationId) {
        Notification notification = notificationRepository.findById(notificationId)
                .filter(n -> n.getUserId().equals(userId))
                .orElseThrow(() -> AppException.notFound("존재하지 않는 알림입니다."));

        notification.markAsRead();

        return new NotificationReadResponse(notification.getNotificationId(), true);
    }

    private NotificationListResponse.NotificationItem toItem(Notification notification) {
        return new NotificationListResponse.NotificationItem(
                notification.getNotificationId(),
                notification.getCategory().name(),
                notification.getTitle(),
                notification.getContent(),
                notification.isRead(),
                notification.getSentAt());
    }
}
```

- [ ] **Step 6: 서비스 테스트 통과 확인**

Run: `./gradlew test --tests "bigbang.butilkka_be.notification.NotificationServiceTest" --console=plain`
Expected: `BUILD SUCCESSFUL`, 9개 테스트 모두 PASS

- [ ] **Step 7: 컨트롤러 테스트에 읽음 처리 테스트 추가 (실패 확인)**

`src/test/java/bigbang/butilkka_be/notification/NotificationControllerTest.java`의 import 목록에 추가:

```java
import bigbang.butilkka_be.notification.dto.NotificationReadResponse;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
```

같은 파일의 마지막 `@Test` 메서드 뒤(클래스 닫는 `}` 앞)에 테스트 추가:

```java

    @Test
    void markAsRead_returnsOk() throws Exception {
        when(notificationService.markAsRead(eq(1L), eq(301L))).thenReturn(
                new NotificationReadResponse(301L, true));

        mockMvc.perform(patch("/api/v1/notifications/301")
                        .with(authentication(authAs("1"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.notificationId").value(301))
                .andExpect(jsonPath("$.data.isRead").value(true));
    }
```

Run: `./gradlew test --tests "bigbang.butilkka_be.notification.NotificationControllerTest" --console=plain`
Expected: FAIL — `NotificationController`에 `/{notificationId}` PATCH 매핑이 없어 404 응답이거나, `NotificationService.markAsRead`가 없어 컴파일 에러

- [ ] **Step 8: `NotificationController`에 엔드포인트 추가**

`src/main/java/bigbang/butilkka_be/notification/NotificationController.java` 전체를 아래로 교체:

```java
package bigbang.butilkka_be.notification;

import bigbang.butilkka_be.common.response.ApiResponse;
import bigbang.butilkka_be.notification.dto.NotificationListResponse;
import bigbang.butilkka_be.notification.dto.NotificationReadResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;

    @GetMapping
    public ResponseEntity<ApiResponse<NotificationListResponse>> getNotifications(
            @AuthenticationPrincipal String userId,
            @RequestParam(defaultValue = "0") int offset,
            @RequestParam(defaultValue = "20") int limit) {
        NotificationListResponse response = notificationService.getNotifications(Long.parseLong(userId), offset, limit);
        return ResponseEntity.ok(ApiResponse.ok("알림 목록 조회 성공", response));
    }

    @PatchMapping("/{notificationId}")
    public ResponseEntity<ApiResponse<NotificationReadResponse>> markAsRead(
            @AuthenticationPrincipal String userId,
            @PathVariable Long notificationId) {
        NotificationReadResponse response = notificationService.markAsRead(Long.parseLong(userId), notificationId);
        return ResponseEntity.ok(ApiResponse.ok("알림 읽음 처리 성공", response));
    }
}
```

- [ ] **Step 9: 테스트 통과 확인**

Run: `./gradlew test --tests "bigbang.butilkka_be.notification.*" --console=plain`
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 10: 커밋**

```bash
git add src/main/java/bigbang/butilkka_be/notification src/test/java/bigbang/butilkka_be/notification
git commit -m "Add PATCH /api/v1/notifications/{notificationId} endpoint"
```

---

### Task 3: 전체 검증

**Files:** 없음 (기존 파일 재확인만 수행)

- [ ] **Step 1: 전체 테스트 스위트 실행**

Run: `./gradlew test --console=plain`
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 2: 앱 실행 후 인증 없이 2개 엔드포인트가 401 또는 403을 반환하는지 확인**

Run: `docker compose up -d mysql` (이미 떠 있으면 스킵)
Run: `./gradlew bootRun --console=plain` (백그라운드 실행)

Run: `curl -s -o /dev/null -w "%{http_code}\n" "http://localhost:8080/api/v1/notifications"`
Expected: `401` 또는 `403` (Spring Security 기본 익명 거부 동작 — 이전 플랜들에서 이미 확인된 것과 동일)

Run: `curl -s -o /dev/null -w "%{http_code}\n" -X PATCH "http://localhost:8080/api/v1/notifications/1"`
Expected: `401` 또는 `403`

- [ ] **Step 3: 앱 종료**

- [ ] **Step 4: 최종 상태 확인**

Run: `git log --oneline -4`
Expected: Task 1~2의 커밋(notifications 목록, 읽음 처리)이 순서대로 보임
