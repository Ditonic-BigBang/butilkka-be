-- V33이 실패했거나 부분 실행된 경우를 안전하게 처리

-- 1. report_alternative_regions: 기존 FK가 있으면 삭제
SET @fk_exists = (SELECT COUNT(*) FROM information_schema.TABLE_CONSTRAINTS
    WHERE CONSTRAINT_NAME = 'FK_report_alternative_regions_region'
    AND TABLE_NAME = 'report_alternative_regions'
    AND TABLE_SCHEMA = DATABASE());
SET @sql = IF(@fk_exists > 0,
    'ALTER TABLE report_alternative_regions DROP FOREIGN KEY FK_report_alternative_regions_region',
    'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 2. 기존 데이터 삭제 (스키마 변경 전)
DELETE FROM report_alternative_regions;

-- 3. 기존 컬럼 삭제 (있으면)
SET @col_reason = (SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_NAME = 'report_alternative_regions' AND COLUMN_NAME = 'reason' AND TABLE_SCHEMA = DATABASE());
SET @sql = IF(@col_reason > 0,
    'ALTER TABLE report_alternative_regions DROP COLUMN reason',
    'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @col_stat = (SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_NAME = 'report_alternative_regions' AND COLUMN_NAME = 'stat' AND TABLE_SCHEMA = DATABASE());
SET @sql = IF(@col_stat > 0,
    'ALTER TABLE report_alternative_regions DROP COLUMN stat',
    'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 4. 새 컬럼 추가 (없으면)
SET @col_rank = (SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_NAME = 'report_alternative_regions' AND COLUMN_NAME = 'rank_order' AND TABLE_SCHEMA = DATABASE());
SET @sql = IF(@col_rank = 0,
    'ALTER TABLE report_alternative_regions ADD COLUMN rank_order INT NOT NULL DEFAULT 1 AFTER region_code',
    'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @col_msg = (SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_NAME = 'report_alternative_regions' AND COLUMN_NAME = 'ai_message' AND TABLE_SCHEMA = DATABASE());
SET @sql = IF(@col_msg = 0,
    'ALTER TABLE report_alternative_regions ADD COLUMN ai_message TEXT NULL AFTER rank_order',
    'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 5. reports 테이블: ai_rec_* 컬럼 추가 (없으면)
SET @col_badge = (SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_NAME = 'reports' AND COLUMN_NAME = 'ai_rec_badge_type' AND TABLE_SCHEMA = DATABASE());
SET @sql = IF(@col_badge = 0,
    'ALTER TABLE reports ADD COLUMN ai_rec_badge_type VARCHAR(20) NULL AFTER decision_description',
    'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @col_title = (SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_NAME = 'reports' AND COLUMN_NAME = 'ai_rec_title' AND TABLE_SCHEMA = DATABASE());
SET @sql = IF(@col_title = 0,
    'ALTER TABLE reports ADD COLUMN ai_rec_title VARCHAR(100) NULL AFTER ai_rec_badge_type',
    'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @col_reason_title = (SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_NAME = 'reports' AND COLUMN_NAME = 'ai_rec_reason_title' AND TABLE_SCHEMA = DATABASE());
SET @sql = IF(@col_reason_title = 0,
    'ALTER TABLE reports ADD COLUMN ai_rec_reason_title VARCHAR(100) NULL AFTER ai_rec_title',
    'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @col_reason_detail = (SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_NAME = 'reports' AND COLUMN_NAME = 'ai_rec_reason_detail' AND TABLE_SCHEMA = DATABASE());
SET @sql = IF(@col_reason_detail = 0,
    'ALTER TABLE reports ADD COLUMN ai_rec_reason_detail TEXT NULL AFTER ai_rec_reason_title',
    'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
