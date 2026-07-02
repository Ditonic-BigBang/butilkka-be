# 온보딩 API 구현 및 상권코드 실제 행정동 전환 설계

## 배경

노션 "API명세서 V2" 데이터베이스 기준으로 온보딩 화면의 API 3개를 구현한다:

- `GET /api/v1/categories` — 업종 목록 조회
- `PUT /api/v1/users/me/store` — 가게 위치·업종 설정/수정
- `GET /api/v1/regions/lookup` — 좌표·주소 → 상권코드 매핑

구현 과정에서 스펙의 `regionCode`가 가리키는 대상과 기존 `regions` 목업 테이블의 코드 체계가 서로 다르다는 문제를 발견했다. 기존 `/api/v1/lookup`(어제 pull된 기능)은 서울시 GeoJSON 425개 실제 행정동 폴리곤을 기준으로 좌표를 판정해 법정동 코드(예: `1168064000`)를 반환하는데, `regions` 테이블은 임의로 만든 35개 상권 코드(예: `3110002`)를 쓰고 있어 두 코드 체계가 호환되지 않는다. 이 상태로는 `/api/v1/regions/lookup`이 반환한 코드로 `PUT /api/v1/users/me/store`를 호출해도 검증에 실패한다.

## 결정: GeoJSON 실제 행정동 기준으로 전체 통합

`regions` 테이블을 425개 실제 행정동 데이터로 전체 교체하고, 기존 mock 데이터(reports, commercial_stats, user_interest_regions, users.store_region, report_alternative_regions, report_similar_cases)가 참조하던 35개 코드를 매핑되는 실제 행정동 코드로 일괄 마이그레이션한다. `districts`(자치구) 테이블은 이미 실제 법정동 코드 체계(`11680` 강남구 등)를 쓰고 있어 변경하지 않는다.

## 1. DB 마이그레이션

### 1-1. regions 테이블 전체 교체

- 기존 35개 행 삭제, GeoJSON(`src/main/resources/geojson/seoul.geojson`)의 425개 feature에서 `region_code`=`adm_cd2`, `region_name`=`adm_nm`의 마지막 동 이름, `district_code`=`sgg`를 추출해 INSERT
- 425개 행은 손으로 작성하지 않고 GeoJSON을 읽어 SQL을 생성하는 스크립트로 마이그레이션 파일을 만든다

### 1-2. 기존 데이터의 legacy region_code → 실제 행정동 코드 매핑

아래 매핑표로 `reports.region_code`, `commercial_stats.region_code`, `user_interest_regions.region_code`, `users.store_region`, `report_alternative_regions.region_code`, `report_similar_cases.region_code`를 UPDATE한다.

| 기존 코드 | 상권명 | 실제 행정동 코드 | 행정동명 |
|---|---|---|---|
| 3110001 | 가로수길 | 1168051000 | 신사동 |
| 3110002 | 강남역 | 1168064000 | 역삼1동 |
| 3110003 | 압구정로데오 | 1168054500 | 압구정동 |
| 3110004 | 청담동 | 1168056500 | 청담동 |
| 3110005 | 삼성역 | 1168058000 | 삼성1동 |
| 3110006 | 선릉역 | 1168059000 | 삼성2동 |
| 3110007 | 역삼역 | 1168065000 | 역삼2동 |
| 3110008 | 논현동 | 1168052100 | 논현1동 |
| 3120001 | 서초역 | 1165052000 | 서초2동 |
| 3120002 | 교대역 | 1165053000 | 서초3동 |
| 3120003 | 방배동카페거리 | 1165059000 | 방배본동 |
| 3120004 | 양재역 | 1165065100 | 양재1동 |
| 3130001 | 홍대입구역 | 1144066000 | 서교동 |
| 3130002 | 합정역 | 1144068000 | 합정동 |
| 3130003 | 상수동 | 1144065500 | 서강동 |
| 3130004 | 연남동 | 1144071000 | 연남동 |
| 3130005 | 망원동 | 1144069000 | 망원1동 |
| 3140001 | 건대입구역 | 1121571000 | 화양동 |
| 3140002 | 구의역 | 1121585000 | 구의1동 |
| 3150001 | 이수역 | 1159062000 | 사당1동 |
| 3150002 | 사당역 | 1159065000 | 사당4동 |
| 3160001 | 여의도 | 1156054000 | 여의동 |
| 3160002 | 영등포역 | 1156053500 | 영등포동 |
| 3170001 | 잠실역 | 1171065000 | 잠실본동 |
| 3170002 | 석촌호수 | 1171060000 | 석촌동 |
| 3170003 | 송리단길 | 1171058000 | 송파1동 |
| 3180001 | 이태원 | 1117065000 | 이태원1동 |
| 3180002 | 한남동 | 1117068500 | 한남동 |
| 3180003 | 경리단길 | 1117066000 | 이태원2동 |
| 3190001 | 익선동 | 1111061500 | 종로1·2·3·4가동 |
| 3190002 | 북촌 | 1111060000 | 가회동 |
| 3190003 | 광화문 | 1111053000 | 사직동 |
| 3200001 | 성수동 | 1120065000 | 성수1가1동 |
| 3200002 | 서울숲 | 1120069000 | 성수2가3동 |
| 3210001 | 마곡역 | 1150061100 | 발산1동 |
| 3220001 | 노원역 | 1135069500 | 상계6·7동 |

## 2. 신규/변경 API

### 2-1. `GET /api/v1/categories`

- 신규 `CategoryController` → `CategoryService` → `CategoryRepository.findAll()`
- 인증 필요(JWT, 기존 필터 재사용), 요청 파라미터 없음
- 응답: `{code, status, message, data: [{categoryCode, categoryName}]}`

### 2-2. `PUT /api/v1/users/me/store`

- 기존 `UserController`/`UserService` 확장
- Request: `regionCode`, `categoryCode`, `lat`, `lng`, `storeName`, `storeOpenDate`
- `regionCode`가 `RegionRepository`에 없거나 `categoryCode`가 `CategoryRepository`에 없으면 `AppException.badRequest("존재하지 않는 상권코드 또는 업종 코드입니다")`
- `User`에 `updateStore(regionCode, categoryCode, lat, lng, storeName, storeOpenDate)` 도메인 메서드 신설 (현재 setter 없음) — 호출 시 `isOnboarded = true`로 전환
- 응답 데이터는 갱신된 정보 + 조인한 `regionName`/`categoryName` 포함

### 2-3. `GET /api/v1/regions/lookup`

- 신규 컨트롤러/서비스 (`region` 패키지). 기존 `/api/v1/lookup`(`lookup` 패키지)은 변경하지 않고 그대로 둔다.
- `keyword` 또는 `(lat, lng)` 중 정확히 하나만 필수 — 둘 다 없거나 둘 다 있으면 400
- `(lat, lng)` 모드: `lookup.RegionLookupService`의 point-in-polygon 로직을 재사용해 매칭되는 GeoJSON feature를 찾음 (이제 `regions` 테이블과 코드 체계가 동일하므로 그대로 `regionCode`로 사용 가능)
- `keyword` 모드: `RegionRepository`에 `region_name LIKE %keyword%` 쿼리 추가해 검색
- 응답 배열 항목: `regionCode`, `regionName`, `address`(= `"서울특별시 " + districtName + " " + regionName"`로 조합, 신규 컬럼 없이 생성), `lat`/`lng`(GeoJSON 폴리곤의 centroid로 계산 — 스펙에 "보류" 표기되어 있어 근사값 처리)
- 매칭 결과 없으면 404 `"매칭되는 상권이 없습니다."`

## 3. 목업 데이터 확장 — 리포트 히스토리

- 김민수(역삼1동, 한식음식점) 상권에 대해 1~4분기 리포트를 모두 생성 (현재는 4분기만 존재)
  - 등급 변화 스토리: 1분기 B → 2분기 A → 3분기 C → 4분기 A 등 자연스러운 흐름으로 구성
  - 각 분기 리포트마다 `report_cause`, `report_signal`, `report_decision_reasons`, `report_similar_cases`, `report_alternative_regions`도 함께 추가
- `GET /api/v1/reportsHistory` 호출 시 한 유저에 대해 여러 건의 리포트가 시간순으로 나오는 것을 확인 가능
- 나머지 4명 유저의 기존 데이터는 변경하지 않음

## 에러 처리

기존 컨벤션 유지: `AppException`(badRequest/notFound/conflict/unauthorized) + `GlobalExceptionHandler`가 `ApiResponse` 포맷으로 변환. 새 코드도 동일한 패턴을 따른다.

## 테스트

- `CategoryServiceTest`, `CategoryControllerTest` (신규)
- `UserServiceTest`의 `updateStore` 케이스 (정상/잘못된 regionCode/잘못된 categoryCode)
- 신규 `RegionsLookupControllerTest`/`RegionsLookupServiceTest`: keyword 검색, lat/lng 검색, 둘 다 없음/둘 다 있음 400, 매칭 없음 404
- 마이그레이션 후 기존 `RegionLookupServiceTest`, `LookupControllerTest`가 여전히 통과하는지 확인 (regionCode 포맷이 바뀌므로 테스트 픽스처 점검 필요)
