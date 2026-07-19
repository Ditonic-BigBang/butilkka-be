-- reports.region_code가 구 기반(district_code, 5자리)으로 전환되었으나
-- FK가 여전히 regions(10자리 행정동 코드) 테이블을 참조하고 있어
-- 리포트 생성 시 FK 제약 위반(500 에러)이 발생하는 문제 수정

-- 기존 FK(regions 참조)를 먼저 제거해야 아래 UPDATE에서
-- 5자리 구코드로 값을 바꿀 때 FK 위반이 나지 않음
ALTER TABLE reports DROP FOREIGN KEY FK_reports_region;

-- 기존 데이터(10자리 행정동 코드)를 앞 5자리 구코드로 정규화
UPDATE reports SET region_code = SUBSTRING(region_code, 1, 5) WHERE LENGTH(region_code) = 10;

ALTER TABLE reports ADD CONSTRAINT FK_reports_region
    FOREIGN KEY (region_code) REFERENCES districts (district_code);
