-- 외부 유사 사례용 placeholder 자치구 추가
INSERT INTO districts (district_code, district_name)
VALUES ('EXT-00', '외부사례');

-- 외부 유사 사례용 placeholder 상권 추가 (모든 외부 사례에 공통 사용)
INSERT INTO regions (region_code, region_name, district_code)
VALUES ('EXT-CASE', '외부사례', 'EXT-00');
