-- reports 테이블: AI 추천 카드 필드 추가
ALTER TABLE reports
    ADD COLUMN ai_rec_badge_type VARCHAR(20) NULL COMMENT 'AI 추천 배지 타입' AFTER decision_description,
    ADD COLUMN ai_rec_title VARCHAR(100) NULL COMMENT 'AI 추천 제목' AFTER ai_rec_badge_type,
    ADD COLUMN ai_rec_reason_title VARCHAR(100) NULL COMMENT 'AI 추천 이유 제목' AFTER ai_rec_title,
    ADD COLUMN ai_rec_reason_detail TEXT NULL COMMENT 'AI 추천 이유 상세' AFTER ai_rec_reason_title;

-- report_alternative_regions 테이블: 스키마 변경
-- 기존 reason, stat 컬럼 삭제, rank_order, ai_message 추가
-- FK 제약 해제 (region_code가 district_code로 변경되므로)
ALTER TABLE report_alternative_regions
    DROP FOREIGN KEY FK_report_alternative_regions_region;

-- 기존 데이터 삭제 (FK 있어도 DELETE는 가능)
DELETE FROM report_alternative_regions;

-- 컬럼 변경
ALTER TABLE report_alternative_regions
    DROP COLUMN reason,
    DROP COLUMN stat,
    ADD COLUMN rank_order INT NOT NULL COMMENT '순위' AFTER region_code,
    ADD COLUMN ai_message TEXT NULL COMMENT 'AI 생성 메시지' AFTER rank_order;
