-- reports: year, score 추가
ALTER TABLE reports
    ADD COLUMN year SMALLINT NOT NULL DEFAULT 2026 COMMENT '연도' AFTER quarter;
ALTER TABLE reports
    ALTER COLUMN year DROP DEFAULT;

ALTER TABLE reports
    ADD COLUMN score TINYINT NULL COMMENT '상권 점수 (0~100)' AFTER grade;

UPDATE reports SET score = CASE grade
    WHEN 'A' THEN 90
    WHEN 'B' THEN 70
    WHEN 'C' THEN 50
    WHEN 'D' THEN 30
    WHEN 'E' THEN 10
END;

ALTER TABLE reports MODIFY COLUMN score TINYINT NOT NULL COMMENT '상권 점수 (0~100)';

-- report_cause: description 추가
ALTER TABLE report_cause
    ADD COLUMN description VARCHAR(255) NULL COMMENT '원인 설명' AFTER level;

UPDATE report_cause SET description = '연말 프로모션과 모임 수요 증가로 매출이 늘고 있습니다' WHERE report_id = 1 AND title = '연말 시즌 소비 증가';
UPDATE report_cause SET description = '인근 오피스 근무자 유입이 늘어 유동인구가 증가했습니다' WHERE report_id = 1 AND title = '직장인 유동인구 증가';
UPDATE report_cause SET description = '인근에 신규 오피스가 들어서며 잠재 고객층이 확대되었습니다' WHERE report_id = 1 AND title = '신규 오피스 빌딩 입주';
UPDATE report_cause SET description = '외국인 관광객 방문이 늘며 매출에 긍정적 영향을 주고 있습니다' WHERE report_id = 2 AND title = '관광객 유입 증가';
UPDATE report_cause SET description = '감성 카페 트렌드 확산으로 신규 고객 유입이 늘고 있습니다' WHERE report_id = 2 AND title = '카페 트렌드 변화';
UPDATE report_cause SET description = '전반적인 소비 심리 위축으로 매출이 감소하고 있습니다' WHERE report_id = 3 AND title = '경기 침체 영향';
UPDATE report_cause SET description = '배달앱 내 경쟁 점포 증가로 주문이 분산되고 있습니다' WHERE report_id = 3 AND title = '배달앱 경쟁 심화';
UPDATE report_cause SET description = '치킨 원육 등 원자재 가격 상승으로 수익성이 악화되고 있습니다' WHERE report_id = 3 AND title = '원자재 가격 상승';
UPDATE report_cause SET description = '성수동 상권 전체의 인기 상승에 따른 낙수 효과가 나타나고 있습니다' WHERE report_id = 4 AND title = '성수동 핫플 효과';
UPDATE report_cause SET description = '20대 고객 비중이 늘며 트렌디한 서비스 수요가 증가했습니다' WHERE report_id = 4 AND title = '젊은층 유입';
UPDATE report_cause SET description = '유동인구가 소폭 늘며 안정적인 매출 기반이 유지되고 있습니다' WHERE report_id = 5 AND title = '완만한 유동인구 증가';
UPDATE report_cause SET description = '임대료 변동이 크지 않아 고정비 부담이 안정적입니다' WHERE report_id = 5 AND title = '안정적 임대료 수준';
UPDATE report_cause SET description = '전분기에 이어 매출 상승세가 이어지고 있습니다' WHERE report_id = 6 AND title = '매출 상승 지속';
UPDATE report_cause SET description = '인근 경쟁 점포 수가 줄며 상대적으로 수요를 흡수하고 있습니다' WHERE report_id = 6 AND title = '경쟁 점포 감소';
UPDATE report_cause SET description = '계절적 비수기로 유동인구와 매출이 일시적으로 감소했습니다' WHERE report_id = 7 AND title = '여름 비수기 영향';
UPDATE report_cause SET description = '비수기 영향으로 인근 폐업률이 소폭 상승했습니다' WHERE report_id = 7 AND title = '일시적 폐업률 상승';

ALTER TABLE report_cause MODIFY COLUMN description VARCHAR(255) NOT NULL COMMENT '원인 설명';

-- report_alternative_regions: stat 추가
ALTER TABLE report_alternative_regions
    ADD COLUMN stat VARCHAR(50) NULL COMMENT '핵심 지표' AFTER reason;

UPDATE report_alternative_regions SET stat = '유동인구 +12.8%' WHERE report_id = 3 AND region_code = '1120065000';
UPDATE report_alternative_regions SET stat = '임대료 -15.3%' WHERE report_id = 3 AND region_code = '1171058000';
UPDATE report_alternative_regions SET stat = '폐업률 -2.1%' WHERE report_id = 3 AND region_code = '1144069000';
UPDATE report_alternative_regions SET stat = '유동인구 +1.2%' WHERE report_id = 7 AND region_code = '1168065000';
UPDATE report_alternative_regions SET stat = '유동인구 +3.5%' WHERE report_id = 7 AND region_code = '1168058000';

ALTER TABLE report_alternative_regions MODIFY COLUMN stat VARCHAR(50) NOT NULL COMMENT '핵심 지표';
