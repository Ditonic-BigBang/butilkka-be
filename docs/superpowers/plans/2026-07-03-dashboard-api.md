# 홈 대시보드 API Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 홈 화면의 API 1개(`GET /api/v1/dashboard`)를 구현한다.

**Architecture:** 전용 엔티티 없이 기존 `CommercialStats`(지도/상권)와 `User`를 조회해 응답 DTO로 가공하는 새 `dashboard` 패키지(서비스+컨트롤러)를 추가한다. `gaugeValue`는 코드 레벨 고정 매핑(A=90/B=70/C=50/D=30/E=10)으로 산출하며 마이그레이션은 없다.

**Tech Stack:** Spring Boot 4.1, Spring Data JPA, MySQL 8.0 (Docker), JUnit5 + Mockito + AssertJ + MockMvc, Spring Security Test.

## Global Constraints

- 모든 신규 에러는 기존 `AppException`(notFound) + `GlobalExceptionHandler` 컨벤션을 따른다.
- 모든 신규 응답은 `ApiResponse.ok(message, data)` 포맷을 따른다.
- 신규 엔드포인트는 `SecurityConfig`의 `anyRequest().authenticated()`에 자동 포함되므로 `SecurityConfig` 변경은 불필요하다.
- `@AuthenticationPrincipal`을 사용하는 컨트롤러 테스트는 `@AutoConfigureMockMvc(addFilters = false)`를 쓰지 않고 `@Import(SecurityConfig.class)` + 기본 `@AutoConfigureMockMvc` + `SecurityMockMvcRequestPostProcessors.authentication(...)`을 사용한다.
- 유저 레코드 자체가 없으면(비정상 케이스) 기존 `UserService.getMe`와 동일한 메시지 `AppException.notFound("사용자를 찾을 수 없습니다")`를 사용한다. 가게 미등록(`storeRegion == null`) 또는 해당 상권의 `CommercialStats` 데이터가 하나도 없으면 스펙이 요구하는 404 `"등록된 가게 정보가 없습니다."`를 반환한다 — 두 경우를 구분하지 않고 프론트에는 동일한 메시지로 응답한다.
- `metrics`의 3개 지표(`footTraffic`/`storeCount`/`closureRate`)는 구조가 동일하므로 공통 `MetricTrend(direction, delta, gap, points[])` 레코드 하나를 재사용한다.
- `direction`은 최신 분기 `*_delta`의 부호로 계산한다: 델타가 `null`이거나 0 이상이면 `"UP"`, 음수면 `"DOWN"`. 응답의 `delta`/`gap`은 절댓값으로 노출한다.
- `CommercialStats`의 `footTraffic`/`storeCount`/`closureRate`와 `*_delta`/`*_gap` 컬럼은 스키마상 nullable이므로, `MetricTrend.delta`/`gap`과 `Point.value`는 각각 boxed `Double`/`Long`/`Double`로 선언해 언박싱 NPE를 방지한다(리포트 화면 최종 리뷰에서 지적됐던 것과 동일한 클래스의 문제를 사전에 방지).
- `points[]`는 상권 이력의 마지막 최대 3개 분기를 `{quarter: "{year}Q{quarter}", value}`로 변환한다(3개 미만이면 있는 만큼만).
- `grade.previous`는 상권 이력이 2개 분기 미만이면 `null`이다.

---

### Task 1: `GET /api/v1/dashboard`

**Files:**
- Create: `src/main/java/bigbang/butilkka_be/dashboard/dto/DashboardResponse.java`
- Create: `src/main/java/bigbang/butilkka_be/dashboard/DashboardService.java`
- Create: `src/main/java/bigbang/butilkka_be/dashboard/DashboardController.java`
- Test: `src/test/java/bigbang/butilkka_be/dashboard/DashboardServiceTest.java`
- Test: `src/test/java/bigbang/butilkka_be/dashboard/DashboardControllerTest.java`

**Interfaces:**
- Consumes: 기존 `UserRepository.findById(Long): Optional<User>`, `User.getStoreRegion(): String`, `User.getCategoryCode(): String`; `CommercialStatsQueryService.historyForRegion(String): List<CommercialStats>`; `CommercialStats.getYear(): Integer`, `getQuarter(): Integer`, `getDeclineGrade(): String`, `getBriefing(): String`, `getFootTraffic(): Integer`, `getFootTrafficDelta(): BigDecimal`, `getFootTrafficGap(): Long`, `getStoreCount(): Integer`, `getStoreCountDelta(): BigDecimal`, `getStoreCountGap(): Long`, `getClosureRate(): BigDecimal`, `getClosureRateDelta(): BigDecimal`, `getClosureRateGap(): Long`; `RegionRepository.findById(String): Optional<Region>`, `Region.getRegionCode(): String`, `getRegionName(): String`, `getDistrictCode(): String`; `DistrictRepository.findById(String): Optional<District>`, `District.getDistrictName(): String`; `CategoryRepository.findById(String): Optional<Category>`, `Category.getCategoryName(): String`
- Produces: `DashboardService.getDashboard(Long userId): DashboardResponse` — 이 태스크로 완결 (다른 태스크가 재사용하지 않음)

- [ ] **Step 1: DTO 작성**

`src/main/java/bigbang/butilkka_be/dashboard/dto/DashboardResponse.java`:

```java
package bigbang.butilkka_be.dashboard.dto;

import java.util.List;

public record DashboardResponse(
        StoreInfo store,
        Grade grade,
        String briefing,
        Metrics metrics
) {
    public record StoreInfo(String regionCode, String regionName, String categoryName, String district) {}

    public record Grade(String current, String previous, int gaugeValue) {}

    public record Metrics(MetricTrend footTraffic, MetricTrend storeCount, MetricTrend closureRate) {}

    public record MetricTrend(String direction, Double delta, Long gap, List<Point> points) {}

    public record Point(String quarter, Double value) {}
}
```

- [ ] **Step 2: 실패하는 서비스 테스트 작성**

`src/test/java/bigbang/butilkka_be/dashboard/DashboardServiceTest.java`:

```java
package bigbang.butilkka_be.dashboard;

import bigbang.butilkka_be.category.Category;
import bigbang.butilkka_be.category.CategoryRepository;
import bigbang.butilkka_be.common.exception.AppException;
import bigbang.butilkka_be.dashboard.dto.DashboardResponse;
import bigbang.butilkka_be.region.District;
import bigbang.butilkka_be.region.DistrictRepository;
import bigbang.butilkka_be.region.Region;
import bigbang.butilkka_be.region.RegionRepository;
import bigbang.butilkka_be.stats.CommercialStats;
import bigbang.butilkka_be.stats.CommercialStatsQueryService;
import bigbang.butilkka_be.user.User;
import bigbang.butilkka_be.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DashboardServiceTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private CommercialStatsQueryService commercialStatsQueryService;
    @Mock
    private RegionRepository regionRepository;
    @Mock
    private DistrictRepository districtRepository;
    @Mock
    private CategoryRepository categoryRepository;

    private DashboardService service;

    @BeforeEach
    void setUp() {
        service = new DashboardService(
                userRepository, commercialStatsQueryService, regionRepository, districtRepository, categoryRepository);
    }

    private static User userWithStore(String regionCode, String categoryCode) {
        User user = mock(User.class);
        lenient().when(user.getStoreRegion()).thenReturn(regionCode);
        lenient().when(user.getCategoryCode()).thenReturn(categoryCode);
        return user;
    }

    private void stubRegionDistrictCategory() {
        Region region = mock(Region.class);
        when(region.getRegionCode()).thenReturn("1168064000");
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

    private static CommercialStats statsOf(
            int year, int quarter, String grade, String briefing,
            Integer footTraffic, String footTrafficDelta, Long footTrafficGap,
            Integer storeCount, String storeCountDelta, Long storeCountGap,
            String closureRate, String closureRateDelta, Long closureRateGap) {
        CommercialStats stats = mock(CommercialStats.class);
        lenient().when(stats.getYear()).thenReturn(year);
        lenient().when(stats.getQuarter()).thenReturn(quarter);
        lenient().when(stats.getDeclineGrade()).thenReturn(grade);
        lenient().when(stats.getBriefing()).thenReturn(briefing);
        lenient().when(stats.getFootTraffic()).thenReturn(footTraffic);
        lenient().when(stats.getFootTrafficDelta()).thenReturn(toBigDecimal(footTrafficDelta));
        lenient().when(stats.getFootTrafficGap()).thenReturn(footTrafficGap);
        lenient().when(stats.getStoreCount()).thenReturn(storeCount);
        lenient().when(stats.getStoreCountDelta()).thenReturn(toBigDecimal(storeCountDelta));
        lenient().when(stats.getStoreCountGap()).thenReturn(storeCountGap);
        lenient().when(stats.getClosureRate()).thenReturn(toBigDecimal(closureRate));
        lenient().when(stats.getClosureRateDelta()).thenReturn(toBigDecimal(closureRateDelta));
        lenient().when(stats.getClosureRateGap()).thenReturn(closureRateGap);
        return stats;
    }

    private static BigDecimal toBigDecimal(String value) {
        return value == null ? null : new BigDecimal(value);
    }

    @Test
    void getDashboard_returnsFullDashboard() {
        User user = userWithStore("1168064000", "CS100001");
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        stubRegionDistrictCategory();

        CommercialStats q4_2025 = statsOf(2025, 4, "B", "이전 브리핑",
                132423, "0.0", 0L, 412, "0.0", 0L, "3.1", "0.0", 0L);
        CommercialStats q1_2026 = statsOf(2026, 1, "B", "이전 브리핑2",
                128110, "1.0", 100L, 405, "0.5", 2L, "3.4", "0.1", 0L);
        CommercialStats q2_2026 = statsOf(2026, 2, "B", "이전 브리핑3",
                125000, "-1.5", -3110L, 401, "0.7", 3L, "3.7", "0.0", 0L);
        CommercialStats q3_2026 = statsOf(2026, 3, "C", "유동인구 감소와 공실 증가가 겹치는 주의 구간입니다.",
                121940, "-5.5", -6170L, 398, "0.7", 3L, "3.9", "0.0", 0L);
        when(commercialStatsQueryService.historyForRegion("1168064000"))
                .thenReturn(List.of(q4_2025, q1_2026, q2_2026, q3_2026));

        DashboardResponse response = service.getDashboard(1L);

        assertThat(response.store().regionCode()).isEqualTo("1168064000");
        assertThat(response.store().regionName()).isEqualTo("역삼1동");
        assertThat(response.store().district()).isEqualTo("강남구");
        assertThat(response.store().categoryName()).isEqualTo("한식음식점");

        assertThat(response.grade().current()).isEqualTo("C");
        assertThat(response.grade().previous()).isEqualTo("B");
        assertThat(response.grade().gaugeValue()).isEqualTo(50);

        assertThat(response.briefing()).isEqualTo("유동인구 감소와 공실 증가가 겹치는 주의 구간입니다.");

        assertThat(response.metrics().footTraffic().direction()).isEqualTo("DOWN");
        assertThat(response.metrics().footTraffic().delta()).isEqualTo(5.5);
        assertThat(response.metrics().footTraffic().gap()).isEqualTo(6170L);
        assertThat(response.metrics().footTraffic().points()).hasSize(3);
        assertThat(response.metrics().footTraffic().points().get(0).quarter()).isEqualTo("2026Q1");
        assertThat(response.metrics().footTraffic().points().get(0).value()).isEqualTo(128110.0);
        assertThat(response.metrics().footTraffic().points().get(2).quarter()).isEqualTo("2026Q3");
        assertThat(response.metrics().footTraffic().points().get(2).value()).isEqualTo(121940.0);

        assertThat(response.metrics().storeCount().direction()).isEqualTo("UP");
        assertThat(response.metrics().storeCount().delta()).isEqualTo(0.7);
        assertThat(response.metrics().storeCount().gap()).isEqualTo(3L);

        assertThat(response.metrics().closureRate().direction()).isEqualTo("UP");
        assertThat(response.metrics().closureRate().delta()).isEqualTo(0.0);
        assertThat(response.metrics().closureRate().gap()).isEqualTo(0L);
        assertThat(response.metrics().closureRate().points().get(2).value()).isEqualTo(3.9);
    }

    @Test
    void getDashboard_withNoStore_throwsNotFound() {
        User user = userWithStore(null, null);
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> service.getDashboard(1L))
                .isInstanceOf(AppException.class)
                .extracting(e -> ((AppException) e).getHttpStatus())
                .isEqualTo(org.springframework.http.HttpStatus.NOT_FOUND);
    }

    @Test
    void getDashboard_withNoStatsForRegion_throwsNotFound() {
        User user = userWithStore("9999999999", "CS100001");
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(commercialStatsQueryService.historyForRegion("9999999999")).thenReturn(List.of());

        assertThatThrownBy(() -> service.getDashboard(1L))
                .isInstanceOf(AppException.class)
                .extracting(e -> ((AppException) e).getHttpStatus())
                .isEqualTo(org.springframework.http.HttpStatus.NOT_FOUND);
    }

    @Test
    void getDashboard_withOnlyOneQuarter_previousIsNullAndSinglePoint() {
        User user = userWithStore("1168064000", "CS100001");
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        stubRegionDistrictCategory();

        CommercialStats onlyQuarter = statsOf(2026, 1, "A", "첫 분기 브리핑",
                50000, "0.0", 0L, 100, "0.0", 0L, "2.0", "0.0", 0L);
        when(commercialStatsQueryService.historyForRegion("1168064000")).thenReturn(List.of(onlyQuarter));

        DashboardResponse response = service.getDashboard(1L);

        assertThat(response.grade().current()).isEqualTo("A");
        assertThat(response.grade().previous()).isNull();
        assertThat(response.grade().gaugeValue()).isEqualTo(90);
        assertThat(response.metrics().footTraffic().points()).hasSize(1);
        assertThat(response.metrics().footTraffic().points().get(0).quarter()).isEqualTo("2026Q1");
    }

    @Test
    void getDashboard_withNullDeltaGapAndValue_returnsNullsAndDefaultsToUp() {
        User user = userWithStore("1168064000", "CS100001");
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        stubRegionDistrictCategory();

        CommercialStats statsWithNulls = statsOf(2026, 1, "B", "브리핑",
                null, null, null, 100, "0.0", 0L, "2.0", "0.0", 0L);
        when(commercialStatsQueryService.historyForRegion("1168064000")).thenReturn(List.of(statsWithNulls));

        DashboardResponse response = service.getDashboard(1L);

        assertThat(response.metrics().footTraffic().direction()).isEqualTo("UP");
        assertThat(response.metrics().footTraffic().delta()).isNull();
        assertThat(response.metrics().footTraffic().gap()).isNull();
        assertThat(response.metrics().footTraffic().points().get(0).value()).isNull();
    }
}
```

- [ ] **Step 3: 테스트가 실패하는지 확인**

Run: `./gradlew test --tests "bigbang.butilkka_be.dashboard.DashboardServiceTest" --console=plain`
Expected: FAIL — `DashboardService` 클래스가 존재하지 않음 (컴파일 에러)

- [ ] **Step 4: `DashboardService` 구현**

`src/main/java/bigbang/butilkka_be/dashboard/DashboardService.java`:

```java
package bigbang.butilkka_be.dashboard;

import bigbang.butilkka_be.category.Category;
import bigbang.butilkka_be.category.CategoryRepository;
import bigbang.butilkka_be.common.exception.AppException;
import bigbang.butilkka_be.dashboard.dto.DashboardResponse;
import bigbang.butilkka_be.region.District;
import bigbang.butilkka_be.region.DistrictRepository;
import bigbang.butilkka_be.region.Region;
import bigbang.butilkka_be.region.RegionRepository;
import bigbang.butilkka_be.stats.CommercialStats;
import bigbang.butilkka_be.stats.CommercialStatsQueryService;
import bigbang.butilkka_be.user.User;
import bigbang.butilkka_be.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;
import java.util.function.Function;

@Service
@RequiredArgsConstructor
public class DashboardService {

    private final UserRepository userRepository;
    private final CommercialStatsQueryService commercialStatsQueryService;
    private final RegionRepository regionRepository;
    private final DistrictRepository districtRepository;
    private final CategoryRepository categoryRepository;

    public DashboardResponse getDashboard(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> AppException.notFound("사용자를 찾을 수 없습니다"));

        if (user.getStoreRegion() == null) {
            throw AppException.notFound("등록된 가게 정보가 없습니다.");
        }

        List<CommercialStats> history = commercialStatsQueryService.historyForRegion(user.getStoreRegion());
        if (history.isEmpty()) {
            throw AppException.notFound("등록된 가게 정보가 없습니다.");
        }

        CommercialStats latest = history.get(history.size() - 1);
        String previousGrade = history.size() >= 2
                ? history.get(history.size() - 2).getDeclineGrade()
                : null;

        DashboardResponse.StoreInfo store = buildStore(user);
        DashboardResponse.Grade grade = new DashboardResponse.Grade(
                latest.getDeclineGrade(), previousGrade, gaugeValueOf(latest.getDeclineGrade()));

        DashboardResponse.Metrics metrics = new DashboardResponse.Metrics(
                trendOf(history, latest.getFootTrafficDelta(), latest.getFootTrafficGap(), CommercialStats::getFootTraffic),
                trendOf(history, latest.getStoreCountDelta(), latest.getStoreCountGap(), CommercialStats::getStoreCount),
                trendOf(history, latest.getClosureRateDelta(), latest.getClosureRateGap(), CommercialStats::getClosureRate));

        return new DashboardResponse(store, grade, latest.getBriefing(), metrics);
    }

    private DashboardResponse.StoreInfo buildStore(User user) {
        Region region = regionRepository.findById(user.getStoreRegion())
                .orElseThrow(() -> AppException.notFound("존재하지 않는 상권코드입니다."));
        District district = districtRepository.findById(region.getDistrictCode())
                .orElseThrow(() -> AppException.notFound("존재하지 않는 자치구코드입니다."));
        Category category = categoryRepository.findById(user.getCategoryCode())
                .orElseThrow(() -> AppException.notFound("존재하지 않는 업종코드입니다."));
        return new DashboardResponse.StoreInfo(
                region.getRegionCode(), region.getRegionName(), category.getCategoryName(), district.getDistrictName());
    }

    private int gaugeValueOf(String grade) {
        return switch (grade) {
            case "A" -> 90;
            case "B" -> 70;
            case "C" -> 50;
            case "D" -> 30;
            case "E" -> 10;
            default -> throw new IllegalStateException("알 수 없는 상권 등급: " + grade);
        };
    }

    private DashboardResponse.MetricTrend trendOf(
            List<CommercialStats> history, BigDecimal delta, Long gap,
            Function<CommercialStats, Number> valueExtractor) {
        String direction = (delta != null && delta.signum() < 0) ? "DOWN" : "UP";
        Double deltaAbs = delta == null ? null : Math.abs(delta.doubleValue());
        Long gapAbs = gap == null ? null : Math.abs(gap);

        List<DashboardResponse.Point> points = history.stream()
                .skip(Math.max(0, history.size() - 3))
                .map(stats -> new DashboardResponse.Point(
                        stats.getYear() + "Q" + stats.getQuarter(), toDouble(valueExtractor.apply(stats))))
                .toList();

        return new DashboardResponse.MetricTrend(direction, deltaAbs, gapAbs, points);
    }

    private Double toDouble(Number number) {
        return number == null ? null : number.doubleValue();
    }
}
```

- [ ] **Step 5: 서비스 테스트 통과 확인**

Run: `./gradlew test --tests "bigbang.butilkka_be.dashboard.DashboardServiceTest" --console=plain`
Expected: `BUILD SUCCESSFUL`, 5개 테스트 모두 PASS

- [ ] **Step 6: 실패하는 컨트롤러 테스트 작성**

`src/test/java/bigbang/butilkka_be/dashboard/DashboardControllerTest.java`:

```java
package bigbang.butilkka_be.dashboard;

import bigbang.butilkka_be.common.security.JwtTokenProvider;
import bigbang.butilkka_be.common.security.SecurityConfig;
import bigbang.butilkka_be.dashboard.dto.DashboardResponse;
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

@WebMvcTest(DashboardController.class)
@AutoConfigureMockMvc
@Import(SecurityConfig.class)
class DashboardControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private DashboardService dashboardService;

    @MockitoBean
    private JwtTokenProvider jwtTokenProvider;

    private static Authentication authAs(String userId) {
        return new UsernamePasswordAuthenticationToken(userId, null, List.of());
    }

    private static DashboardResponse sampleResponse() {
        DashboardResponse.MetricTrend trend = new DashboardResponse.MetricTrend(
                "DOWN", 5.5, 6170L, List.of(new DashboardResponse.Point("2026Q3", 121940.0)));
        return new DashboardResponse(
                new DashboardResponse.StoreInfo("1168064000", "역삼1동", "한식음식점", "강남구"),
                new DashboardResponse.Grade("C", "B", 50),
                "유동인구 감소와 공실 증가가 겹치는 주의 구간입니다.",
                new DashboardResponse.Metrics(trend, trend, trend));
    }

    @Test
    void getDashboard_returnsOk() throws Exception {
        when(dashboardService.getDashboard(eq(1L))).thenReturn(sampleResponse());

        mockMvc.perform(get("/api/v1/dashboard")
                        .with(authentication(authAs("1"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.store.regionName").value("역삼1동"))
                .andExpect(jsonPath("$.data.grade.current").value("C"))
                .andExpect(jsonPath("$.data.metrics.footTraffic.direction").value("DOWN"));
    }
}
```

- [ ] **Step 7: 테스트가 실패하는지 확인**

Run: `./gradlew test --tests "bigbang.butilkka_be.dashboard.DashboardControllerTest" --console=plain`
Expected: FAIL — `DashboardController` 클래스가 존재하지 않음 (컴파일 에러)

- [ ] **Step 8: `DashboardController` 구현**

`src/main/java/bigbang/butilkka_be/dashboard/DashboardController.java`:

```java
package bigbang.butilkka_be.dashboard;

import bigbang.butilkka_be.common.response.ApiResponse;
import bigbang.butilkka_be.dashboard.dto.DashboardResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/dashboard")
@RequiredArgsConstructor
public class DashboardController {

    private final DashboardService dashboardService;

    @GetMapping
    public ResponseEntity<ApiResponse<DashboardResponse>> getDashboard(
            @AuthenticationPrincipal String userId) {
        DashboardResponse response = dashboardService.getDashboard(Long.parseLong(userId));
        return ResponseEntity.ok(ApiResponse.ok("대시보드 조회 성공", response));
    }
}
```

- [ ] **Step 9: 테스트 통과 확인**

Run: `./gradlew test --tests "bigbang.butilkka_be.dashboard.*" --console=plain`
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 10: 커밋**

```bash
git add src/main/java/bigbang/butilkka_be/dashboard src/test/java/bigbang/butilkka_be/dashboard
git commit -m "Add GET /api/v1/dashboard endpoint"
```

---

### Task 2: 전체 검증

**Files:** 없음 (기존 파일 재확인만 수행)

- [ ] **Step 1: 전체 테스트 스위트 실행**

Run: `./gradlew test --console=plain`
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 2: 앱 실행 후 인증 없이 엔드포인트가 401 또는 403을 반환하는지 확인**

Run: `docker compose up -d mysql` (이미 떠 있으면 스킵)
Run: `./gradlew bootRun --console=plain` (백그라운드 실행)

Run: `curl -s -o /dev/null -w "%{http_code}\n" "http://localhost:8080/api/v1/dashboard"`
Expected: `401` 또는 `403` (Spring Security 기본 익명 거부 동작 — 이전 플랜들에서 이미 확인된 것과 동일)

- [ ] **Step 3: 앱 종료**

- [ ] **Step 4: 최종 상태 확인**

Run: `git log --oneline -3`
Expected: Task 1의 커밋(`GET /api/v1/dashboard`)이 보임
