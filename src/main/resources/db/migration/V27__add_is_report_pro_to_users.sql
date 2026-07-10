-- 사용자 구독 상태 필드 추가
ALTER TABLE users ADD COLUMN is_report_pro BOOLEAN NOT NULL DEFAULT FALSE;
