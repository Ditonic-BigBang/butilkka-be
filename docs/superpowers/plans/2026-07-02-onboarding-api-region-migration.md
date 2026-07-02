# 온보딩 API 구현 및 상권코드 실제 행정동 전환 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 노션 API 명세서의 온보딩 화면 API 3개(업종 목록, 가게 위치/업종 설정, 좌표·주소 상권코드 조회)를 구현하고, 이를 위해 `regions` 목업 테이블을 실제 서울시 425개 행정동 데이터로 전환한다.

**Architecture:** `regions` 테이블을 GeoJSON 기반 실제 행정동 코드로 전체 교체하고 기존 mock 데이터를 리매핑한다. 새 `GET /api/v1/regions/lookup`은 기존 `lookup` 패키지의 point-in-polygon 로직을 재사용하고, `keyword` 검색은 같은 GeoJSON 인메모리 데이터를 대상으로 동작한다. `category`/`user` 패키지에 신규 컨트롤러·서비스를 추가한다.

**Tech Stack:** Spring Boot 4.1, Spring Data JPA, Flyway, MySQL 8.0 (Docker), JTS(locationtech) 지오메트리, JUnit5 + Mockito + AssertJ + MockMvc.

## Global Constraints

- 기존 `/api/v1/lookup`(lookup 패키지)의 동작·응답 포맷은 변경하지 않는다 (하위 호환 유지).
- 모든 신규 에러는 기존 `AppException`(badRequest/notFound) + `GlobalExceptionHandler` 컨벤션을 그대로 따른다.
- 모든 신규 응답은 `ApiResponse.ok(message, data)` 포맷을 따른다.
- 신규 엔드포인트는 `SecurityConfig`의 `anyRequest().authenticated()`에 자동으로 포함되므로 `SecurityConfig` 변경은 불필요하다.
- 테스트는 실제 DB 없이 동작해야 한다 (서비스 테스트는 Mockito, 컨트롤러 테스트는 `@WebMvcTest` + `@MockitoBean`, `@AutoConfigureMockMvc(addFilters = false)`).

---

### Task 1: `regions` 테이블을 실제 425개 행정동으로 교체하는 마이그레이션

**Files:**
- Create: `generate_v18_migration.py` (저장소 루트, 마이그레이션 SQL 생성용 — 실행 후 삭제)
- Create: `src/main/resources/db/migration/V18__replace_regions_with_real_dong_codes.sql` (생성됨)

**Interfaces:**
- Produces: `regions` 테이블에 실제 행정동 코드(예: `1168064000` 역삼1동) 425개 row. 기존 mock 코드(`3110001` 등 36개)는 완전히 삭제되고, 이를 참조하던 `reports`/`commercial_stats`/`user_interest_regions`/`users.store_region`/`report_alternative_regions`/`report_similar_cases`는 대응하는 실제 코드로 갱신됨.
- 이후 태스크에서 사용할 매핑 예시: 강남역/역삼1동 = `1168064000` (CS100001), 가로수길/신사동 = `1168051000`.

- [ ] **Step 1: 마이그레이션 SQL 생성 스크립트 작성**

`generate_v18_migration.py` 파일을 저장소 루트에 아래 내용 그대로 생성한다:

```python
import json

GEOJSON_PATH = "src/main/resources/geojson/seoul.geojson"
OUTPUT_PATH = "src/main/resources/db/migration/V18__replace_regions_with_real_dong_codes.sql"

# 기존 mock 상권코드 -> 실제 행정동 코드 매핑 (온보딩 API 설계 스펙 참조)
LEGACY_TO_REAL = [
    ("3110001", "1168051000"), ("3110002", "1168064000"), ("3110003", "1168054500"),
    ("3110004", "1168056500"), ("3110005", "1168058000"), ("3110006", "1168059000"),
    ("3110007", "1168065000"), ("3110008", "1168052100"), ("3120001", "1165052000"),
    ("3120002", "1165053000"), ("3120003", "1165059000"), ("3120004", "1165065100"),
    ("3130001", "1144066000"), ("3130002", "1144068000"), ("3130003", "1144065500"),
    ("3130004", "1144071000"), ("3130005", "1144069000"), ("3140001", "1121571000"),
    ("3140002", "1121585000"), ("3150001", "1159062000"), ("3150002", "1159065000"),
    ("3160001", "1156054000"), ("3160002", "1156053500"), ("3170001", "1171065000"),
    ("3170002", "1171060000"), ("3170003", "1171058000"), ("3180001", "1117065000"),
    ("3180002", "1117068500"), ("3180003", "1117066000"), ("3190001", "1111061500"),
    ("3190002", "1111060000"), ("3190003", "1111053000"), ("3200001", "1120065000"),
    ("3200002", "1120069000"), ("3210001", "1150061100"), ("3220001", "1135069500"),
]

assert len(LEGACY_TO_REAL) == 36
assert len(set(o for o, n in LEGACY_TO_REAL)) == 36
assert len(set(n for o, n in LEGACY_TO_REAL)) == 36


def build_region_inserts():
    with open(GEOJSON_PATH, encoding="utf-8") as f:
        data = json.load(f)

    rows = []
    for feat in data["features"]:
        p = feat["properties"]
        adm_cd = p["adm_cd2"]
        adm_nm = p["adm_nm"]
        sgg = p["sgg"]
        parts = adm_nm.split(" ")
        dong_name = parts[2] if len(parts) >= 3 else adm_nm
        rows.append((adm_cd, dong_name, sgg))

    rows.sort(key=lambda r: r[0])
    assert len(rows) == 425, f"expected 425 dong rows, got {len(rows)}"

    lines = []
    for i, (code, name, sgg) in enumerate(rows):
        suffix = "," if i < len(rows) - 1 else ";"
        name_escaped = name.replace("'", "''")
        lines.append(f"('{code}', '{name_escaped}', '{sgg}'){suffix}")

    return "INSERT INTO regions (region_code, region_name, district_code) VALUES\n" + "\n".join(lines)


def build_update_and_delete():
    case_lines = "\n".join(f"        WHEN '{o}' THEN '{n}'" for o, n in LEGACY_TO_REAL)
    in_list = ", ".join(f"'{o}'" for o, n in LEGACY_TO_REAL)

    blocks = []
    for table in ["reports", "commercial_stats", "user_interest_regions",
                  "report_alternative_regions", "report_similar_cases"]:
        blocks.append(
            f"UPDATE {table} SET region_code = CASE region_code\n{case_lines}\n"
            f"        ELSE region_code\n    END\nWHERE region_code IN ({in_list});"
        )

    blocks.append(
        f"UPDATE users SET store_region = CASE store_region\n{case_lines}\n"
        f"        ELSE store_region\n    END\nWHERE store_region IN ({in_list});"
    )

    delete_sql = f"DELETE FROM regions WHERE region_code IN ({in_list});"
    return blocks, delete_sql


def main():
    region_inserts = build_region_inserts()
    update_blocks, delete_sql = build_update_and_delete()

    parts = [
        "-- 1) 실제 서울시 425개 행정동을 regions에 추가 (GeoJSON adm_cd2/adm_nm/sgg 기준)",
        region_inserts,
        "",
        "-- 2) 기존 mock 상권 코드(36개)를 참조하던 데이터를 실제 행정동 코드로 일괄 변경",
        *update_blocks,
        "",
        "-- 3) 기존 mock 상권 코드(36개) 삭제 - 이제 아무 데이터도 참조하지 않음",
        delete_sql,
    ]

    with open(OUTPUT_PATH, "w", encoding="utf-8") as f:
        f.write("\n".join(parts) + "\n")

    print(f"wrote {OUTPUT_PATH}")


if __name__ == "__main__":
    main()
```

- [ ] **Step 2: 스크립트 실행**

Run: `PYTHONIOENCODING=utf-8 python3 generate_v18_migration.py`
Expected output: `wrote src/main/resources/db/migration/V18__replace_regions_with_real_dong_codes.sql`
(스크립트 안에 `assert len(rows) == 425`가 있으므로 GeoJSON 파싱이 잘못되면 여기서 바로 실패한다.)

- [ ] **Step 3: 생성된 마이그레이션 파일 검증**

Run: `wc -l src/main/resources/db/migration/V18__replace_regions_with_real_dong_codes.sql`
Expected: 약 670줄 (425개 INSERT row + 6개 UPDATE 블록 + 1개 DELETE)

Run: `grep -c "INSERT INTO regions" src/main/resources/db/migration/V18__replace_regions_with_real_dong_codes.sql`
Expected: `1`

- [ ] **Step 4: 생성 스크립트 삭제 (일회성 도구이므로 저장소에 남기지 않음)**

Run: `rm generate_v18_migration.py`

- [ ] **Step 5: Docker MySQL을 띄우고 앱을 실행해 마이그레이션 적용 확인**

Run: `docker compose up -d mysql` (이미 떠 있으면 스킵)
Run: `DB_URL="jdbc:mysql://localhost:3307/butilkka?serverTimezone=Asia/Seoul&characterEncoding=UTF-8" ./gradlew bootRun --console=plain`
Expected 로그: `Migrating schema \`butilkka\` to version "18 - replace regions with real dong codes"` 및 `Started ButilkkaBeApplication`

- [ ] **Step 6: DB 상태를 직접 조회해 검증**

Run: `docker exec butilkka-be-mysql-1 mysql -uroot -p1234 -e "SELECT COUNT(*) FROM butilkka.regions;"`
Expected: `425`

Run: `docker exec butilkka-be-mysql-1 mysql -uroot -p1234 -e "SELECT COUNT(*) FROM butilkka.regions WHERE region_code IN ('3110001','3110002');"`
Expected: `0`

Run: `docker exec butilkka-be-mysql-1 mysql -uroot -p1234 -e "SELECT store_region FROM butilkka.users WHERE id=1;"`
Expected: `1168064000`

- [ ] **Step 7: 기존 테스트가 여전히 통과하는지 확인 후 앱 종료**

Run: `./gradlew test --console=plain`
Expected: `BUILD SUCCESSFUL`

앱을 종료한다 (bootRun 프로세스 kill).

- [ ] **Step 8: 커밋**

```bash
git add src/main/resources/db/migration/V18__replace_regions_with_real_dong_codes.sql
git commit -m "Replace mock regions table with real Seoul administrative dong codes"
```

---

### Task 2: `RegionLookupService`에 좌표/키워드 검색을 위한 공개 메서드 추가

**Files:**
- Modify: `src/main/java/bigbang/butilkka_be/lookup/RegionLookupService.java`
- Test: `src/test/java/bigbang/butilkka_be/lookup/RegionLookupServiceTest.java`

**Interfaces:**
- Consumes: 없음 (기존 `GeoJsonFeature`, `AppException` 그대로 사용)
- Produces: `public Optional<GeoJsonFeature> findFeatureByCoordinate(double lat, double lng)`, `public List<GeoJsonFeature> searchByKeyword(String keyword)`, `public static String extractDongName(String admName)` — Task 4(RegionsLookupService)에서 그대로 호출한다.

- [ ] **Step 1: 실패하는 테스트 작성**

`src/test/java/bigbang/butilkka_be/lookup/RegionLookupServiceTest.java`의 마지막 테스트 메서드(`parseGeometry_keepsHoleForMultiPolygon`)와 파일 끝의 `private static Point point(...)` 헬퍼 메서드 사이에 다음 테스트 3개를 추가한다 (기존 테스트는 그대로 둔다):

```java
    @Test
    void searchByKeyword_returnsMatchingFeatures() {
        GeoJsonFeature feature = new GeoJsonFeature(
                "1111000000", "서울특별시 테스트구 테스트동", "11110", "테스트구", point(127.0, 37.5));
        ReflectionTestUtils.setField(service, "seoulFeatures", List.of(feature));

        List<GeoJsonFeature> results = service.searchByKeyword("테스트");

        assertThat(results).containsExactly(feature);
    }

    @Test
    void searchByKeyword_withNoMatch_returnsEmptyList() {
        ReflectionTestUtils.setField(service, "seoulFeatures", List.<GeoJsonFeature>of());

        List<GeoJsonFeature> results = service.searchByKeyword("존재하지않음");

        assertThat(results).isEmpty();
    }

    @Test
    void findFeatureByCoordinate_withNoMatch_returnsEmptyOptional() {
        ReflectionTestUtils.setField(service, "seoulFeatures", List.<GeoJsonFeature>of());

        java.util.Optional<GeoJsonFeature> result = service.findFeatureByCoordinate(37.5665, 126.9780);

        assertThat(result).isEmpty();
    }
```

`point(double x, double y)` 헬퍼가 이미 파일 하단에 있으므로 재사용한다 (`private static Point point(...)`).

- [ ] **Step 2: 테스트가 실패하는지 확인**

Run: `./gradlew test --tests "bigbang.butilkka_be.lookup.RegionLookupServiceTest" --console=plain`
Expected: FAIL — `searchByKeyword`/`findFeatureByCoordinate` 심볼을 찾을 수 없음 (컴파일 에러)

- [ ] **Step 3: `RegionLookupService`에 메서드 추가 및 `lookup()` 리팩터링**

`src/main/java/bigbang/butilkka_be/lookup/RegionLookupService.java`의 `lookup(double lat, double lng)` 메서드부터 파일 끝까지를 아래로 교체한다:

```java
    public LookupResponse lookup(double lat, double lng) {
        GeoJsonFeature feature = findFeatureByCoordinate(lat, lng)
                .orElseThrow(() -> AppException.notFound("해당 좌표에 대한 행정동 정보를 찾을 수 없습니다"));

        String dongName = extractDongName(feature.admName());

        return LookupResponse.of(
                feature.admCode(),
                dongName,
                feature.districtCode(),
                feature.districtName()
        );
    }

    public Optional<GeoJsonFeature> findFeatureByCoordinate(double lat, double lng) {
        validateCoordinates(lat, lng);
        Point point = geometryFactory.createPoint(new Coordinate(lng, lat));

        return seoulFeatures.stream()
                .filter(feature -> feature.geometry().covers(point))
                .findFirst();
    }

    public List<GeoJsonFeature> searchByKeyword(String keyword) {
        String normalized = keyword.trim();
        return seoulFeatures.stream()
                .filter(feature -> extractDongName(feature.admName()).contains(normalized))
                .toList();
    }

    private void validateCoordinates(double lat, double lng) {
        if (Double.isNaN(lat) || Double.isNaN(lng) ||
                Double.isInfinite(lat) || Double.isInfinite(lng)) {
            throw AppException.badRequest("유효하지 않은 좌표값입니다");
        }

        if (lat < 37.41 || lat > 37.72 || lng < 126.73 || lng > 127.27) {
            throw AppException.badRequest("서울 범위를 벗어난 좌표입니다");
        }
    }

    public static String extractDongName(String admName) {
        String[] parts = admName.split(" ");
        if (parts.length >= 3) {
            return parts[2];
        }
        return admName;
    }
}
```

(기존 `private Optional<GeoJsonFeature> ...`이나 중복된 `lookup`/`extractDongName` 정의가 남지 않도록, 파일에서 이전 `lookup(double, double)` 메서드 전체와 이전 `private String extractDongName(...)` 메서드를 삭제하고 위 블록으로 교체한다. `import java.util.Optional;`은 이미 파일 상단에 있다.)

- [ ] **Step 4: 테스트 통과 확인**

Run: `./gradlew test --tests "bigbang.butilkka_be.lookup.RegionLookupServiceTest" --console=plain`
Expected: `BUILD SUCCESSFUL`, 8개 테스트 모두 PASS

- [ ] **Step 5: 커밋**

```bash
git add src/main/java/bigbang/butilkka_be/lookup/RegionLookupService.java src/test/java/bigbang/butilkka_be/lookup/RegionLookupServiceTest.java
git commit -m "Expose coordinate/keyword search methods on RegionLookupService"
```

---

### Task 3: `GET /api/v1/regions/lookup` 신규 엔드포인트

**Files:**
- Create: `src/main/java/bigbang/butilkka_be/region/dto/RegionLookupCandidate.java`
- Create: `src/main/java/bigbang/butilkka_be/region/RegionsLookupService.java`
- Create: `src/main/java/bigbang/butilkka_be/region/RegionsLookupController.java`
- Test: `src/test/java/bigbang/butilkka_be/region/RegionsLookupServiceTest.java`
- Test: `src/test/java/bigbang/butilkka_be/region/RegionsLookupControllerTest.java`

**Interfaces:**
- Consumes: `RegionLookupService.findFeatureByCoordinate(double, double)`, `.searchByKeyword(String)`, `.extractDongName(String)` (Task 2에서 생성)
- Produces: `RegionsLookupService.lookup(String keyword, Double lat, Double lng): List<RegionLookupCandidate>` — 컨트롤러에서 사용.

- [ ] **Step 1: DTO 작성**

`src/main/java/bigbang/butilkka_be/region/dto/RegionLookupCandidate.java`:

```java
package bigbang.butilkka_be.region.dto;

public record RegionLookupCandidate(
        String regionCode,
        String regionName,
        String address,
        double lat,
        double lng
) {}
```

- [ ] **Step 2: 실패하는 서비스 테스트 작성**

`src/test/java/bigbang/butilkka_be/region/RegionsLookupServiceTest.java`:

```java
package bigbang.butilkka_be.region;

import bigbang.butilkka_be.common.exception.AppException;
import bigbang.butilkka_be.lookup.RegionLookupService;
import bigbang.butilkka_be.lookup.model.GeoJsonFeature;
import bigbang.butilkka_be.region.dto.RegionLookupCandidate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Polygon;
import org.locationtech.jts.geom.PrecisionModel;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RegionsLookupServiceTest {

    private static final GeometryFactory GEOMETRY_FACTORY =
            new GeometryFactory(new PrecisionModel(), 4326);

    @Mock
    private RegionLookupService regionLookupService;

    private RegionsLookupService service;

    @BeforeEach
    void setUp() {
        service = new RegionsLookupService(regionLookupService);
    }

    private static Polygon square() {
        return GEOMETRY_FACTORY.createPolygon(new Coordinate[]{
                new Coordinate(126.9, 37.5),
                new Coordinate(127.0, 37.5),
                new Coordinate(127.0, 37.55),
                new Coordinate(126.9, 37.55),
                new Coordinate(126.9, 37.5)
        });
    }

    @Test
    void lookup_withBothKeywordAndCoordinate_throwsBadRequest() {
        assertThatThrownBy(() -> service.lookup("역삼", 37.5, 127.0))
                .isInstanceOf(AppException.class)
                .extracting(e -> ((AppException) e).getHttpStatus())
                .isEqualTo(org.springframework.http.HttpStatus.BAD_REQUEST);
    }

    @Test
    void lookup_withNeitherKeywordNorCoordinate_throwsBadRequest() {
        assertThatThrownBy(() -> service.lookup(null, null, null))
                .isInstanceOf(AppException.class)
                .extracting(e -> ((AppException) e).getHttpStatus())
                .isEqualTo(org.springframework.http.HttpStatus.BAD_REQUEST);
    }

    @Test
    void lookup_byKeyword_returnsMatchingCandidates() {
        GeoJsonFeature feature = new GeoJsonFeature(
                "1168064000", "서울특별시 강남구 역삼1동", "11680", "강남구", square());
        when(regionLookupService.searchByKeyword("역삼")).thenReturn(List.of(feature));

        List<RegionLookupCandidate> result = service.lookup("역삼", null, null);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).regionCode()).isEqualTo("1168064000");
        assertThat(result.get(0).regionName()).isEqualTo("역삼1동");
        assertThat(result.get(0).address()).isEqualTo("서울특별시 강남구 역삼1동");
    }

    @Test
    void lookup_byCoordinate_returnsMatchingCandidate() {
        GeoJsonFeature feature = new GeoJsonFeature(
                "1168064000", "서울특별시 강남구 역삼1동", "11680", "강남구", square());
        when(regionLookupService.findFeatureByCoordinate(37.5, 126.95)).thenReturn(Optional.of(feature));

        List<RegionLookupCandidate> result = service.lookup(null, 37.5, 126.95);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).regionCode()).isEqualTo("1168064000");
    }

    @Test
    void lookup_withNoMatch_throwsNotFound() {
        when(regionLookupService.searchByKeyword("존재하지않음")).thenReturn(List.of());

        assertThatThrownBy(() -> service.lookup("존재하지않음", null, null))
                .isInstanceOf(AppException.class)
                .extracting(e -> ((AppException) e).getHttpStatus())
                .isEqualTo(org.springframework.http.HttpStatus.NOT_FOUND);
    }
}
```

- [ ] **Step 3: 테스트가 실패하는지 확인**

Run: `./gradlew test --tests "bigbang.butilkka_be.region.RegionsLookupServiceTest" --console=plain`
Expected: FAIL — `RegionsLookupService` 클래스가 존재하지 않음 (컴파일 에러)

- [ ] **Step 4: `RegionsLookupService` 구현**

`src/main/java/bigbang/butilkka_be/region/RegionsLookupService.java`:

```java
package bigbang.butilkka_be.region;

import bigbang.butilkka_be.common.exception.AppException;
import bigbang.butilkka_be.lookup.RegionLookupService;
import bigbang.butilkka_be.lookup.model.GeoJsonFeature;
import bigbang.butilkka_be.region.dto.RegionLookupCandidate;
import lombok.RequiredArgsConstructor;
import org.locationtech.jts.geom.Point;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class RegionsLookupService {

    private final RegionLookupService regionLookupService;

    public List<RegionLookupCandidate> lookup(String keyword, Double lat, Double lng) {
        boolean hasKeyword = keyword != null && !keyword.isBlank();
        boolean hasCoordinate = lat != null && lng != null;

        if (hasKeyword == hasCoordinate) {
            throw AppException.badRequest("keyword 또는 (lat, lng) 중 하나만 입력해야 합니다");
        }

        List<GeoJsonFeature> features = hasKeyword
                ? regionLookupService.searchByKeyword(keyword)
                : regionLookupService.findFeatureByCoordinate(lat, lng)
                        .map(List::of)
                        .orElseGet(List::of);

        if (features.isEmpty()) {
            throw AppException.notFound("매칭되는 상권이 없습니다.");
        }

        return features.stream().map(this::toCandidate).toList();
    }

    private RegionLookupCandidate toCandidate(GeoJsonFeature feature) {
        String dongName = RegionLookupService.extractDongName(feature.admName());
        String address = "서울특별시 " + feature.districtName() + " " + dongName;
        Point centroid = feature.geometry().getCentroid();
        return new RegionLookupCandidate(feature.admCode(), dongName, address, centroid.getY(), centroid.getX());
    }
}
```

- [ ] **Step 5: 서비스 테스트 통과 확인**

Run: `./gradlew test --tests "bigbang.butilkka_be.region.RegionsLookupServiceTest" --console=plain`
Expected: `BUILD SUCCESSFUL`, 4개 테스트 모두 PASS

- [ ] **Step 6: 실패하는 컨트롤러 테스트 작성**

`src/test/java/bigbang/butilkka_be/region/RegionsLookupControllerTest.java`:

```java
package bigbang.butilkka_be.region;

import bigbang.butilkka_be.common.security.JwtTokenProvider;
import bigbang.butilkka_be.region.dto.RegionLookupCandidate;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(RegionsLookupController.class)
@AutoConfigureMockMvc(addFilters = false)
class RegionsLookupControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private RegionsLookupService regionsLookupService;

    @MockitoBean
    private JwtTokenProvider jwtTokenProvider;

    @Test
    void lookup_byKeyword_returnsOk() throws Exception {
        when(regionsLookupService.lookup(eq("역삼"), isNull(), isNull()))
                .thenReturn(List.of(new RegionLookupCandidate(
                        "1168064000", "역삼1동", "서울특별시 강남구 역삼1동", 37.5, 127.03)));

        mockMvc.perform(get("/api/v1/regions/lookup").param("keyword", "역삼"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].regionName").value("역삼1동"));
    }

    @Test
    void lookup_byCoordinate_returnsOk() throws Exception {
        when(regionsLookupService.lookup(isNull(), eq(37.5), eq(127.03)))
                .thenReturn(List.of(new RegionLookupCandidate(
                        "1168064000", "역삼1동", "서울특별시 강남구 역삼1동", 37.5, 127.03)));

        mockMvc.perform(get("/api/v1/regions/lookup").param("lat", "37.5").param("lng", "127.03"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].regionName").value("역삼1동"));
    }
}
```

- [ ] **Step 7: 테스트가 실패하는지 확인**

Run: `./gradlew test --tests "bigbang.butilkka_be.region.RegionsLookupControllerTest" --console=plain`
Expected: FAIL — `RegionsLookupController` 클래스가 존재하지 않음 (컴파일 에러)

- [ ] **Step 8: `RegionsLookupController` 구현**

`src/main/java/bigbang/butilkka_be/region/RegionsLookupController.java`:

```java
package bigbang.butilkka_be.region;

import bigbang.butilkka_be.common.response.ApiResponse;
import bigbang.butilkka_be.region.dto.RegionLookupCandidate;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/regions")
@RequiredArgsConstructor
public class RegionsLookupController {

    private final RegionsLookupService regionsLookupService;

    @GetMapping("/lookup")
    public ResponseEntity<ApiResponse<List<RegionLookupCandidate>>> lookup(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Double lat,
            @RequestParam(required = false) Double lng) {
        List<RegionLookupCandidate> candidates = regionsLookupService.lookup(keyword, lat, lng);
        return ResponseEntity.ok(ApiResponse.ok("상권 조회 성공", candidates));
    }
}
```

- [ ] **Step 9: 테스트 통과 확인**

Run: `./gradlew test --tests "bigbang.butilkka_be.region.*" --console=plain`
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 10: 커밋**

```bash
git add src/main/java/bigbang/butilkka_be/region src/test/java/bigbang/butilkka_be/region
git commit -m "Add GET /api/v1/regions/lookup endpoint"
```

---

### Task 4: `GET /api/v1/categories` 신규 엔드포인트

**Files:**
- Create: `src/main/java/bigbang/butilkka_be/category/dto/CategoryResponse.java`
- Create: `src/main/java/bigbang/butilkka_be/category/CategoryService.java`
- Create: `src/main/java/bigbang/butilkka_be/category/CategoryController.java`
- Test: `src/test/java/bigbang/butilkka_be/category/CategoryServiceTest.java`
- Test: `src/test/java/bigbang/butilkka_be/category/CategoryControllerTest.java`

**Interfaces:**
- Consumes: 기존 `CategoryRepository extends JpaRepository<Category, String>` (변경 없음)
- Produces: `CategoryService.getCategories(): List<CategoryResponse>` — 컨트롤러에서 사용.

- [ ] **Step 1: DTO 작성**

`src/main/java/bigbang/butilkka_be/category/dto/CategoryResponse.java`:

```java
package bigbang.butilkka_be.category.dto;

import bigbang.butilkka_be.category.Category;

public record CategoryResponse(String categoryCode, String categoryName) {
    public static CategoryResponse from(Category category) {
        return new CategoryResponse(category.getCategoryCode(), category.getCategoryName());
    }
}
```

- [ ] **Step 2: 실패하는 서비스 테스트 작성**

`src/test/java/bigbang/butilkka_be/category/CategoryServiceTest.java`:

```java
package bigbang.butilkka_be.category;

import bigbang.butilkka_be.category.dto.CategoryResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CategoryServiceTest {

    @Mock
    private CategoryRepository categoryRepository;

    @Test
    void getCategories_returnsAllCategoriesAsResponse() {
        Category korean = mock(Category.class);
        when(korean.getCategoryCode()).thenReturn("CS100001");
        when(korean.getCategoryName()).thenReturn("한식음식점");
        Category cafe = mock(Category.class);
        when(cafe.getCategoryCode()).thenReturn("CS100006");
        when(cafe.getCategoryName()).thenReturn("커피전문점");
        when(categoryRepository.findAll()).thenReturn(List.of(korean, cafe));

        CategoryService categoryService = new CategoryService(categoryRepository);
        List<CategoryResponse> result = categoryService.getCategories();

        assertThat(result).containsExactly(
                new CategoryResponse("CS100001", "한식음식점"),
                new CategoryResponse("CS100006", "커피전문점"));
    }
}
```

- [ ] **Step 3: 테스트가 실패하는지 확인**

Run: `./gradlew test --tests "bigbang.butilkka_be.category.CategoryServiceTest" --console=plain`
Expected: FAIL — `CategoryService` 클래스가 존재하지 않음 (컴파일 에러)

- [ ] **Step 4: `CategoryService` 구현**

`src/main/java/bigbang/butilkka_be/category/CategoryService.java`:

```java
package bigbang.butilkka_be.category;

import bigbang.butilkka_be.category.dto.CategoryResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CategoryService {

    private final CategoryRepository categoryRepository;

    public List<CategoryResponse> getCategories() {
        return categoryRepository.findAll().stream()
                .map(CategoryResponse::from)
                .toList();
    }
}
```

- [ ] **Step 5: 서비스 테스트 통과 확인**

Run: `./gradlew test --tests "bigbang.butilkka_be.category.CategoryServiceTest" --console=plain`
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 6: 실패하는 컨트롤러 테스트 작성**

`src/test/java/bigbang/butilkka_be/category/CategoryControllerTest.java`:

```java
package bigbang.butilkka_be.category;

import bigbang.butilkka_be.category.dto.CategoryResponse;
import bigbang.butilkka_be.common.security.JwtTokenProvider;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(CategoryController.class)
@AutoConfigureMockMvc(addFilters = false)
class CategoryControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CategoryService categoryService;

    @MockitoBean
    private JwtTokenProvider jwtTokenProvider;

    @Test
    void getCategories_returnsOkWithList() throws Exception {
        when(categoryService.getCategories())
                .thenReturn(List.of(new CategoryResponse("CS100001", "한식음식점")));

        mockMvc.perform(get("/api/v1/categories"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].categoryName").value("한식음식점"));
    }
}
```

- [ ] **Step 7: 테스트가 실패하는지 확인**

Run: `./gradlew test --tests "bigbang.butilkka_be.category.CategoryControllerTest" --console=plain`
Expected: FAIL — `CategoryController` 클래스가 존재하지 않음 (컴파일 에러)

- [ ] **Step 8: `CategoryController` 구현**

`src/main/java/bigbang/butilkka_be/category/CategoryController.java`:

```java
package bigbang.butilkka_be.category;

import bigbang.butilkka_be.category.dto.CategoryResponse;
import bigbang.butilkka_be.common.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/categories")
@RequiredArgsConstructor
public class CategoryController {

    private final CategoryService categoryService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<CategoryResponse>>> getCategories() {
        List<CategoryResponse> categories = categoryService.getCategories();
        return ResponseEntity.ok(ApiResponse.ok("업종 목록 조회 성공", categories));
    }
}
```

- [ ] **Step 9: 테스트 통과 확인**

Run: `./gradlew test --tests "bigbang.butilkka_be.category.*" --console=plain`
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 10: 커밋**

```bash
git add src/main/java/bigbang/butilkka_be/category src/test/java/bigbang/butilkka_be/category
git commit -m "Add GET /api/v1/categories endpoint"
```

---

### Task 5: `User` 도메인에 `updateStore` 메서드 추가

**Files:**
- Modify: `src/main/java/bigbang/butilkka_be/user/User.java`
- Test: `src/test/java/bigbang/butilkka_be/user/UserTest.java` (신규)

**Interfaces:**
- Produces: `public void updateStore(String regionCode, String categoryCode, Double lat, Double lng, String storeName, LocalDate storeOpenDate)` — Task 6(UserService)에서 호출.

- [ ] **Step 1: 실패하는 테스트 작성**

`src/test/java/bigbang/butilkka_be/user/UserTest.java`:

```java
package bigbang.butilkka_be.user;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class UserTest {

    @Test
    void updateStore_setsFieldsAndMarksOnboarded() {
        User user = User.create(123L, "테스트");

        user.updateStore("1168064000", "CS100001", 37.5, 127.0, "테스트가게", LocalDate.of(2020, 1, 1));

        assertThat(user.isOnboarded()).isTrue();
        assertThat(user.getStoreRegion()).isEqualTo("1168064000");
        assertThat(user.getCategoryCode()).isEqualTo("CS100001");
        assertThat(user.getStoreLat()).isEqualTo(37.5);
        assertThat(user.getStoreLng()).isEqualTo(127.0);
        assertThat(user.getStoreName()).isEqualTo("테스트가게");
        assertThat(user.getStoreOpenDate()).isEqualTo(LocalDate.of(2020, 1, 1));
    }
}
```

- [ ] **Step 2: 테스트가 실패하는지 확인**

Run: `./gradlew test --tests "bigbang.butilkka_be.user.UserTest" --console=plain`
Expected: FAIL — `updateStore` 메서드가 존재하지 않음 (컴파일 에러)

- [ ] **Step 3: `User.updateStore` 구현**

`src/main/java/bigbang/butilkka_be/user/User.java`의 `public static User create(...)` 메서드 바로 뒤에 아래 메서드를 추가한다:

```java
    public void updateStore(
            String regionCode,
            String categoryCode,
            Double lat,
            Double lng,
            String storeName,
            LocalDate storeOpenDate) {
        this.storeRegion = regionCode;
        this.categoryCode = categoryCode;
        this.storeLat = lat;
        this.storeLng = lng;
        this.storeName = storeName;
        this.storeOpenDate = storeOpenDate;
        this.isOnboarded = true;
    }
```

- [ ] **Step 4: 테스트 통과 확인**

Run: `./gradlew test --tests "bigbang.butilkka_be.user.UserTest" --console=plain`
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 5: 커밋**

```bash
git add src/main/java/bigbang/butilkka_be/user/User.java src/test/java/bigbang/butilkka_be/user/UserTest.java
git commit -m "Add User.updateStore domain method"
```

---

### Task 6: `UserService.updateStore` — regionCode/categoryCode 검증 로직

**Files:**
- Create: `src/main/java/bigbang/butilkka_be/user/dto/StoreUpdateRequest.java`
- Create: `src/main/java/bigbang/butilkka_be/user/dto/StoreResponse.java`
- Modify: `src/main/java/bigbang/butilkka_be/user/UserService.java`
- Test: `src/test/java/bigbang/butilkka_be/user/UserServiceTest.java` (신규)

**Interfaces:**
- Consumes: `RegionRepository extends JpaRepository<Region, String>`, `CategoryRepository extends JpaRepository<Category, String>` (둘 다 기존 코드, 변경 없음), `User.updateStore(...)` (Task 5)
- Produces: `UserService.updateStore(Long userId, StoreUpdateRequest request): StoreResponse` — Task 7(UserController)에서 호출.

- [ ] **Step 1: DTO 작성**

`src/main/java/bigbang/butilkka_be/user/dto/StoreUpdateRequest.java`:

```java
package bigbang.butilkka_be.user.dto;

import java.time.LocalDate;

public record StoreUpdateRequest(
        String regionCode,
        String categoryCode,
        Double lat,
        Double lng,
        String storeName,
        LocalDate storeOpenDate
) {}
```

`src/main/java/bigbang/butilkka_be/user/dto/StoreResponse.java`:

```java
package bigbang.butilkka_be.user.dto;

import bigbang.butilkka_be.user.User;

import java.time.LocalDate;

public record StoreResponse(
        String regionCode,
        String regionName,
        String categoryCode,
        String categoryName,
        Double lat,
        Double lng,
        String storeName,
        LocalDate storeOpenDate
) {
    public static StoreResponse of(User user, String regionName, String categoryName) {
        return new StoreResponse(
                user.getStoreRegion(),
                regionName,
                user.getCategoryCode(),
                categoryName,
                user.getStoreLat(),
                user.getStoreLng(),
                user.getStoreName(),
                user.getStoreOpenDate());
    }
}
```

- [ ] **Step 2: 실패하는 서비스 테스트 작성**

`src/test/java/bigbang/butilkka_be/user/UserServiceTest.java`:

```java
package bigbang.butilkka_be.user;

import bigbang.butilkka_be.category.Category;
import bigbang.butilkka_be.category.CategoryRepository;
import bigbang.butilkka_be.common.exception.AppException;
import bigbang.butilkka_be.region.Region;
import bigbang.butilkka_be.region.RegionRepository;
import bigbang.butilkka_be.user.dto.StoreResponse;
import bigbang.butilkka_be.user.dto.StoreUpdateRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private RegionRepository regionRepository;
    @Mock
    private CategoryRepository categoryRepository;

    @InjectMocks
    private UserService userService;

    @Test
    void updateStore_withValidCodes_updatesUserAndReturnsMergedResponse() {
        User user = User.create(1L, "김민수");
        Region region = mock(Region.class);
        when(region.getRegionName()).thenReturn("역삼1동");
        Category category = mock(Category.class);
        when(category.getCategoryName()).thenReturn("한식음식점");

        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(regionRepository.findById("1168064000")).thenReturn(Optional.of(region));
        when(categoryRepository.findById("CS100001")).thenReturn(Optional.of(category));

        StoreUpdateRequest request = new StoreUpdateRequest(
                "1168064000", "CS100001", 37.5, 127.03, "민수네 한식당", LocalDate.of(2022, 3, 15));

        StoreResponse response = userService.updateStore(1L, request);

        assertThat(response.regionName()).isEqualTo("역삼1동");
        assertThat(response.categoryName()).isEqualTo("한식음식점");
        assertThat(user.isOnboarded()).isTrue();
    }

    @Test
    void updateStore_withUnknownRegionCode_throwsBadRequest() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(User.create(1L, "김민수")));
        when(regionRepository.findById("UNKNOWN")).thenReturn(Optional.empty());

        StoreUpdateRequest request = new StoreUpdateRequest(
                "UNKNOWN", "CS100001", 37.5, 127.03, "가게", LocalDate.of(2022, 3, 15));

        assertThatThrownBy(() -> userService.updateStore(1L, request))
                .isInstanceOf(AppException.class)
                .extracting(e -> ((AppException) e).getHttpStatus())
                .isEqualTo(org.springframework.http.HttpStatus.BAD_REQUEST);
    }

    @Test
    void updateStore_withUnknownCategoryCode_throwsBadRequest() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(User.create(1L, "김민수")));
        when(regionRepository.findById("1168064000")).thenReturn(Optional.of(mock(Region.class)));
        when(categoryRepository.findById("UNKNOWN")).thenReturn(Optional.empty());

        StoreUpdateRequest request = new StoreUpdateRequest(
                "1168064000", "UNKNOWN", 37.5, 127.03, "가게", LocalDate.of(2022, 3, 15));

        assertThatThrownBy(() -> userService.updateStore(1L, request))
                .isInstanceOf(AppException.class)
                .extracting(e -> ((AppException) e).getHttpStatus())
                .isEqualTo(org.springframework.http.HttpStatus.BAD_REQUEST);
    }
}
```

- [ ] **Step 3: 테스트가 실패하는지 확인**

Run: `./gradlew test --tests "bigbang.butilkka_be.user.UserServiceTest" --console=plain`
Expected: FAIL — `UserService.updateStore` 메서드가 존재하지 않음 (컴파일 에러)

- [ ] **Step 4: `UserService.updateStore` 구현**

`src/main/java/bigbang/butilkka_be/user/UserService.java` 전체를 아래로 교체한다:

```java
package bigbang.butilkka_be.user;

import bigbang.butilkka_be.category.Category;
import bigbang.butilkka_be.category.CategoryRepository;
import bigbang.butilkka_be.common.exception.AppException;
import bigbang.butilkka_be.region.Region;
import bigbang.butilkka_be.region.RegionRepository;
import bigbang.butilkka_be.user.dto.StoreResponse;
import bigbang.butilkka_be.user.dto.StoreUpdateRequest;
import bigbang.butilkka_be.user.dto.UserResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserService {

    private final UserRepository userRepository;
    private final RegionRepository regionRepository;
    private final CategoryRepository categoryRepository;

    public UserResponse getMe(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> AppException.notFound("사용자를 찾을 수 없습니다"));
        return UserResponse.from(user);
    }

    @Transactional
    public StoreResponse updateStore(Long userId, StoreUpdateRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> AppException.notFound("사용자를 찾을 수 없습니다"));

        Region region = regionRepository.findById(request.regionCode())
                .orElseThrow(() -> AppException.badRequest("존재하지 않는 상권코드 또는 업종 코드입니다"));
        Category category = categoryRepository.findById(request.categoryCode())
                .orElseThrow(() -> AppException.badRequest("존재하지 않는 상권코드 또는 업종 코드입니다"));

        user.updateStore(
                request.regionCode(),
                request.categoryCode(),
                request.lat(),
                request.lng(),
                request.storeName(),
                request.storeOpenDate());

        return StoreResponse.of(user, region.getRegionName(), category.getCategoryName());
    }
}
```

- [ ] **Step 5: 테스트 통과 확인**

Run: `./gradlew test --tests "bigbang.butilkka_be.user.UserServiceTest" --console=plain`
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 6: 커밋**

```bash
git add src/main/java/bigbang/butilkka_be/user/UserService.java src/main/java/bigbang/butilkka_be/user/dto/StoreUpdateRequest.java src/main/java/bigbang/butilkka_be/user/dto/StoreResponse.java src/test/java/bigbang/butilkka_be/user/UserServiceTest.java
git commit -m "Add UserService.updateStore with region/category validation"
```

---

### Task 7: `PUT /api/v1/users/me/store` 엔드포인트

**Files:**
- Modify: `src/main/java/bigbang/butilkka_be/user/UserController.java`
- Test: `src/test/java/bigbang/butilkka_be/user/UserControllerTest.java` (신규)

**Interfaces:**
- Consumes: `UserService.updateStore(Long, StoreUpdateRequest): StoreResponse` (Task 6)

- [ ] **Step 1: 실패하는 컨트롤러 테스트 작성**

`src/test/java/bigbang/butilkka_be/user/UserControllerTest.java`:

```java
package bigbang.butilkka_be.user;

import bigbang.butilkka_be.common.security.JwtTokenProvider;
import bigbang.butilkka_be.user.dto.StoreResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(UserController.class)
@AutoConfigureMockMvc(addFilters = false)
class UserControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private UserService userService;

    @MockitoBean
    private JwtTokenProvider jwtTokenProvider;

    @Test
    void updateStore_withValidRequest_returnsOk() throws Exception {
        when(userService.updateStore(eq(1L), any()))
                .thenReturn(new StoreResponse(
                        "1168064000", "역삼1동", "CS100001", "한식음식점",
                        37.5, 127.03, "민수네 한식당", LocalDate.of(2022, 3, 15)));

        Authentication auth = new UsernamePasswordAuthenticationToken("1", null, List.of());

        mockMvc.perform(put("/api/v1/users/me/store")
                        .with(authentication(auth))
                        .contentType("application/json")
                        .content("""
                                {
                                  "regionCode": "1168064000",
                                  "categoryCode": "CS100001",
                                  "lat": 37.5,
                                  "lng": 127.03,
                                  "storeName": "민수네 한식당",
                                  "storeOpenDate": "2022-03-15"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.regionName").value("역삼1동"));
    }
}
```

- [ ] **Step 2: 테스트가 실패하는지 확인**

Run: `./gradlew test --tests "bigbang.butilkka_be.user.UserControllerTest" --console=plain`
Expected: FAIL — `PUT /api/v1/users/me/store`가 없어 404 반환 (테스트에서 200 기대와 불일치)

- [ ] **Step 3: `UserController`에 PUT 엔드포인트 추가**

`src/main/java/bigbang/butilkka_be/user/UserController.java` 전체를 아래로 교체한다:

```java
package bigbang.butilkka_be.user;

import bigbang.butilkka_be.common.response.ApiResponse;
import bigbang.butilkka_be.user.dto.StoreResponse;
import bigbang.butilkka_be.user.dto.StoreUpdateRequest;
import bigbang.butilkka_be.user.dto.UserResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
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

    @PutMapping("/me/store")
    public ResponseEntity<ApiResponse<StoreResponse>> updateStore(
            @AuthenticationPrincipal String userId,
            @RequestBody StoreUpdateRequest request) {
        StoreResponse response = userService.updateStore(Long.parseLong(userId), request);
        return ResponseEntity.ok(ApiResponse.ok("가게 정보 저장 성공", response));
    }
}
```

- [ ] **Step 4: 테스트 통과 확인**

Run: `./gradlew test --tests "bigbang.butilkka_be.user.*" --console=plain`
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 5: 커밋**

```bash
git add src/main/java/bigbang/butilkka_be/user/UserController.java src/test/java/bigbang/butilkka_be/user/UserControllerTest.java
git commit -m "Add PUT /api/v1/users/me/store endpoint"
```

---

### Task 8: 김민수 리포트 히스토리 목업 데이터 보강 (V19)

**Files:**
- Create: `src/main/resources/db/migration/V19__seed_gangnam_report_history.sql`

**Interfaces:**
- Consumes: Task 1에서 확정된 실제 코드 `1168064000`(역삼1동), `CS100001`(한식음식점), `user_id=1`(김민수, V17에서 이미 존재)
- Produces: `reports` 테이블에 `user_id=1, region_code='1168064000'` 조합의 1~4분기 리포트 4건 (1~3분기는 이번에 추가, 4분기는 V17에서 이미 존재).

- [ ] **Step 1: 마이그레이션 파일 작성**

`src/main/resources/db/migration/V19__seed_gangnam_report_history.sql`:

```sql
-- 김민수(강남역/역삼1동 한식) 리포트 히스토리 보강: 1~3분기 리포트 추가 (4분기는 V17에서 이미 존재)
-- V17 시점에 report_id 1~4가 이미 사용되었으므로, 아래 INSERT는 순서대로 5, 6, 7번 report_id를 받는다.

INSERT INTO reports (user_id, region_code, category_code, quarter, grade, decline_type, summary, ai_outlook, decision_recommendation, decision_title, decision_description) VALUES
(1, '1168064000', 'CS100001', 1, 'B', '순환형',
 '완만한 성장세 속 안정적인 상권 유지',
 '강남역 한식 상권은 안정적인 흐름을 보이고 있습니다. 유동인구는 전분기 대비 5.2% 증가했으며 매출도 3.5% 상승했습니다. 30대 남성 고객층이 주력이며 경쟁 점포 수는 소폭 감소했습니다. 임대료 상승폭은 크지 않아 수익성에 큰 영향은 없습니다. 전반적으로 무난한 흐름이 이어지고 있습니다.',
 '버티기', '현 위치 유지 권장',
 '상권이 안정적으로 유지되고 있어 현재 위치에서 영업을 지속하는 것이 적절합니다. 급격한 변화는 없으나 꾸준한 매출 성장이 나타나고 있으니 단골 고객 관리에 집중하세요.'),
(1, '1168064000', 'CS100001', 2, 'A', '성장형',
 '매출 상승세 지속과 임대료 안정화로 최상위 등급 도달',
 '강남역 한식 상권은 2분기 들어 뚜렷한 성장세를 보였습니다. 유동인구가 5.6% 증가했고 매출은 8.2%나 상승했습니다. 임대료는 안정적으로 유지되어 수익성 개선에 긍정적입니다. 점포 수는 소폭 감소해 경쟁 부담이 줄었습니다. 전반적으로 매우 긍정적인 흐름입니다.',
 '버티기', '성장 흐름 유지 권장',
 '현재 상권이 성장세에 있으므로 매장 확장이나 신메뉴 출시 등 공격적인 전략을 고려해볼 만합니다. 임대료가 안정적인 지금이 투자 적기입니다.'),
(1, '1168064000', 'CS100001', 3, 'C', '순환형',
 '여름 비수기 영향으로 매출 조정, 폐업률 소폭 상승',
 '3분기 들어 강남역 한식 상권은 계절적 비수기 영향을 받았습니다. 유동인구가 3.0% 감소했고 매출도 4.3% 줄었습니다. 폐업률은 4.8%로 소폭 상승했으며 공실률도 함께 올랐습니다. 다만 이는 계절적 요인이 큰 것으로 분석되며 구조적 쇠퇴로 보기는 어렵습니다.',
 '버티기', '비수기 대응 전략 필요',
 '계절적 비수기로 인한 일시적 조정으로 판단됩니다. 배달/포장 비중을 늘리거나 여름 시즌 메뉴를 개발해 매출 방어에 집중하세요. 4분기 반등이 예상되므로 무리한 이동은 권장하지 않습니다.');

INSERT INTO report_cause (report_id, title, level) VALUES
(5, '완만한 유동인구 증가', '중간'),
(5, '안정적 임대료 수준', '낮음'),
(6, '매출 상승 지속', '높음'),
(6, '경쟁 점포 감소', '중간'),
(7, '여름 비수기 영향', '높음'),
(7, '일시적 폐업률 상승', '중간');

INSERT INTO report_signal (report_id, title, description) VALUES
(5, '안정적 성장', '유동인구 5.2% 증가, 매출 3.5% 상승'),
(6, '매출 급등', '전분기 대비 매출 8.2% 상승'),
(7, '비수기 조정', '매출 4.3% 감소, 계절적 요인으로 분석');

INSERT INTO report_decision_reasons (id, report_id, reason_1, reason_2, reason_3) VALUES
('550e8400-e29b-41d4-a716-446655440005', 5, '유동인구 5.2% 증가', '매출 3.5% 상승', '안정적 임대료 수준'),
('550e8400-e29b-41d4-a716-446655440006', 6, '매출 8.2% 상승', '임대료 안정화', '경쟁 점포 감소'),
('550e8400-e29b-41d4-a716-446655440007', 7, '매출 4.3% 감소', '폐업률 4.8%로 상승', '계절적 비수기 요인');

INSERT INTO report_similar_cases (id, report_id, region_code, summary, description, start_year, end_year, tag1, tag2, tag3, tag4) VALUES
('660e8400-e29b-41d4-a716-446655440005', 6, '1168051000', '신사동 가로수길 한식 상권 성장기', '2019년부터 프리미엄 한식 트렌드와 함께 급성장. 고급화 전략으로 성공한 사례가 많음.', 2019, 2022, '프리미엄', '한식', '성장', '차별화');

INSERT INTO report_alternative_regions (report_id, region_code, reason) VALUES
(7, '1168065000', '역삼2동: 유동인구 안정적, 임대료 수준 유사'),
(7, '1168058000', '삼성1동: 오피스 상권으로 점심 수요 꾸준');
```

- [ ] **Step 2: 앱을 실행해 마이그레이션 적용 확인**

Run: `docker compose up -d mysql` (이미 떠 있으면 스킵)
Run: `DB_URL="jdbc:mysql://localhost:3307/butilkka?serverTimezone=Asia/Seoul&characterEncoding=UTF-8" ./gradlew bootRun --console=plain`
Expected 로그: `Migrating schema \`butilkka\` to version "19 - seed gangnam report history"`, `Started ButilkkaBeApplication`

- [ ] **Step 3: DB에서 김민수 리포트가 4건인지 확인**

Run: `docker exec butilkka-be-mysql-1 mysql -uroot -p1234 -e "SELECT report_id, quarter, grade FROM butilkka.reports WHERE user_id=1 ORDER BY quarter;"`
Expected: 4개 행 (quarter 1~4), grade 순서대로 `B, A, C, A`

- [ ] **Step 4: 앱 종료 후 커밋**

```bash
git add src/main/resources/db/migration/V19__seed_gangnam_report_history.sql
git commit -m "Add 1-3 quarter report history for user 1 to demo report history list"
```

---

### Task 9: 전체 검증

**Files:** 없음 (기존 파일 재확인만 수행)

- [ ] **Step 1: 전체 테스트 스위트 실행**

Run: `./gradlew test --console=plain`
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 2: 앱을 실행해 3개 신규 엔드포인트 스모크 테스트**

Run: `docker compose up -d mysql`
Run: `DB_URL="jdbc:mysql://localhost:3307/butilkka?serverTimezone=Asia/Seoul&characterEncoding=UTF-8" ./gradlew bootRun --console=plain` (백그라운드 실행)

로그인 토큰이 없는 상태이므로 401을 반환하는 것으로 인증 적용을 우선 확인한다:

Run: `curl -s -o /dev/null -w "%{http_code}\n" http://localhost:8080/api/v1/categories`
Expected: `401` (인증 필요 확인)

Run: `curl -s -o /dev/null -w "%{http_code}\n" "http://localhost:8080/api/v1/regions/lookup?lat=37.5665&lng=126.9780"`
Expected: `401`

Run: `curl -s -o /dev/null -w "%{http_code}\n" -X PUT http://localhost:8080/api/v1/users/me/store -H "Content-Type: application/json" -d '{}'`
Expected: `401`

- [ ] **Step 3: 앱 종료**

bootRun 프로세스를 종료한다.

- [ ] **Step 4: 최종 상태 확인**

Run: `git log --oneline -10`
Expected: Task 1~8의 커밋 9개(V18, RegionLookupService 리팩터링, regions/lookup, categories, User.updateStore, UserService.updateStore, users/me/store, V19)가 순서대로 보임
