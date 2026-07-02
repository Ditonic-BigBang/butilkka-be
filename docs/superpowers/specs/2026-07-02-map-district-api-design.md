# 지도/상권 화면 API 구현 설계

## 배경

노션 "API명세서 V2" 기준 지도/상권 화면의 API 7개를 구현한다:

- `GET /api/v1/regions/map` — 지도 색상 데이터
- `GET /api/v1/regions/declineRanking` — 쇠퇴 등급 Top5 순위
- `GET /api/v1/regions/search` — 상권 검색
- `GET /api/v1/districts/{districtsCode}` — 특정 상권 상세
- `POST /api/v1/favorites` — 관심 상권 추가
- `GET /api/v1/favorites` — 관심 상권(즐겨찾기) 목록
- `DELETE /api/v1/favorites/{regionCode}` — 관심 상권 삭제

이 화면의 API는 대부분 `commercial_stats`(상권 통계) 데이터를 다양한 형태로 가공해 내려주는데, 현재 이 테이블에는 425개 상권 중 5개 상권에만 데이터가 있고, 그마저도 `year` 컬럼이 없어 분기(1~4)만 구분된다. "특정 상권 상세" API는 12분기(3년)치 추이를 요구하므로 스키마 확장이 필요하다.

## 알려진 스펙 이슈

"특정 상권 상세" API는 노션 스펙상 URI가 `/api/v1/districts/{districtsCode}`이고 PathVariable 설명은 `regionCode`로 되어 있어 표기가 엇갈린다(자치구가 아니라 상권 상세를 다루는 내용). 이번 구현은 **URI를 스펙 그대로** `/api/v1/districts/{districtsCode}` 사용하되, 실제로는 상권코드(regionCode)로 취급한다.

## 1. 데이터 모델 변경

### 1-1. `commercial_stats`에 `year` 컬럼 추가

기존 20개 행(5개 상권 × 4분기)은 모두 `year = 2026`으로 백필한다.

### 1-2. 히어로 상권 3곳에 2024~2025년 데이터 추가 (12분기 추이 확보)

- 역삼1동(`1168064000`)/한식(`CS100001`, 김민수)
- 서교동(`1144066000`)/카페(`CS100006`, 이영희)
- 성수1가1동(`1120065000`)/미용실(`CS200001`, 정수현)

각 상권에 2024 Q1~Q4, 2025 Q1~Q4 데이터를 추가해 기존 2026년 4개 분기와 합쳐 12분기 연속 이력을 만든다. 등급은 완만한 개선 흐름(D/C → B → A 방향)으로 구성해 `declineRanking`의 `direction`(UP/DOWN/FLAT)이 의미 있게 계산되도록 한다.

### 1-3. 지도/랭킹 화면용 상권 12곳 추가

나머지 425개 상권 중 서로 다른 자치구에 걸친 12곳(이태원1동, 여의동, 서초2동, 연남동, 압구정동, 청담동, 가회동, 한남동, 잠실2동, 합정동, 회현동, 화양동)에 업종 1개씩 배정하고 2026년 4분기(최신) 데이터 1건씩만 추가한다. 등급은 A~E가 고루 섞이도록 구성해 지도 색상과 Top5/Bottom5 랭킹이 다양하게 나타나도록 한다.

결과적으로 17개 상권이 최소 "현재 등급"을 가지며, 이 중 3개는 12분기 전체 추이를 가진다.

### 1-4. 관심 상권은 기존 `user_interest_regions` 재사용

이미 `alias`, `sort_order` 컬럼까지 있어 신규 엔티티/테이블이 필요 없다. `UserInterestRegion`/`UserInterestRegionRepository`를 그대로 사용한다.

## 2. API별 설계

### 2-1. `GET /api/v1/regions/map`

- Query: `quarter`(옵션, 예: `2026Q1`) — 미지정 시 상권별 최신 분기.
- 상권별로 (미지정 시) 가장 최근 `(year, quarter)` 행 하나를 골라 `regionCode/regionName/district/grade` 배열로 반환.
- 데이터가 없는 상권은 응답에서 제외한다 (425개 전체를 내려주지 않음).
- `quarter` 형식이 `YYYYQ[1-4]`가 아니면 400 `"지원하지 않는 지표입니다."`

### 2-2. `GET /api/v1/regions/declineRanking`

- Query: `order`(top/bottom, 필수), `quarter`(옵션, 미지정 시 최신).
- 최신(또는 지정) 분기 기준 등급으로 정렬 후 상위 5개 반환. `order=top`은 등급이 좋은(A에 가까운) 순, `order=bottom`은 나쁜(E에 가까운) 순.
- `direction`은 해당 상권의 직전 분기 등급과 비교해 계산(UP=개선, DOWN=악화, FLAT=동일 또는 직전 분기 데이터 없음).
- `order` 값이 `top`/`bottom`이 아니면 400 `"지원하지 않는 지표 또는 정렬 기준입니다."`

### 2-3. `GET /api/v1/regions/search`

- Query: `keyword`(필수).
- `region_name LIKE %keyword%`로 검색, `district_code`로 `District`를 조인해 자치구명 포함.
- `keyword`가 비어있으면 400 `"검색어를 입력해주세요."`

### 2-4. `GET /api/v1/districts/{districtsCode}`

- PathVariable은 상권코드(regionCode)로 취급.
- 해당 상권의 모든 `commercial_stats` 행을 연도·분기 순으로 정렬해 최대 12개 확보.
- 6개 지표(declineGrade/rentRatio/footTraffic/vacancyRate/closureRate/storeCount) 각각에 대해 `current`(최신), `previous`(직전), `trend[]`(연도·분기별 값)를 구성.
- `rentRatio.value`는 별도 비율 계산 없이 기존 `rent_amount` 값을 그대로 사용한다.
- `closureRate`엔 `avgOperatingYears`(=`avg_business_period`), `seoulAvgOperatingYears`(서울 평균, 고정값 사용)를 추가.
- `storeCount.categoryDistribution`은 해당 상권에 연결된 카테고리 기준으로 구성(대부분 1개 항목).
- 상권코드가 `regions`에 없으면 404 `"존재하지 않는 상권코드입니다."`

### 2-5. `POST /api/v1/favorites`

- Request: `regionCode`.
- `regions`에 없는 코드면 400.
- 이미 3개 등록되어 있으면 409 `"관심 상권은 최대 3개까지 등록할 수 있습니다."`
- 이미 등록된 상권이면 409 `"이미 등록된 관심 상권입니다."` (스펙엔 명시되지 않았지만 중복 방지를 위한 보강)
- 성공 시 201, 추가된 `regionCode/regionName/district` 반환.

### 2-6. `GET /api/v1/favorites`

- 로그인 유저의 `user_interest_regions` 목록을 조회하고, 각 상권의 최신 분기 등급을 조인해 `regionCode/regionName/district/grade` 배열로 반환.

### 2-7. `DELETE /api/v1/favorites/{regionCode}`

- 로그인 유저의 관심 상권 중 해당 코드가 없으면 404 `"등록되지 않은 관심 상권입니다."`
- 성공 시 200, `data: null`.

## 에러 처리

기존 컨벤션 유지: `AppException`(badRequest/notFound/conflict) + `GlobalExceptionHandler` → `ApiResponse` 포맷.

## 테스트

- `CommercialStatsRepository`에 추가하는 최신-분기 조회 쿼리에 대한 서비스 레벨 단위 테스트
- `RegionsMapServiceTest`, `RegionsMapControllerTest` (지도 색상)
- `RegionsRankingServiceTest`, `RegionsRankingControllerTest` (Top5/Bottom5, direction 계산 포함)
- `RegionsSearchServiceTest`, `RegionsSearchControllerTest`
- `RegionDetailServiceTest`, `RegionDetailControllerTest` (12분기 trend 구성, 404)
- `FavoriteServiceTest`, `FavoriteControllerTest` (추가/목록/삭제, 3개 제한, 중복 방지, 404)
