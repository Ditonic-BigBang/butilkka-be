-- 소수점 4자리로 설정 (× 100 시 소수점 2자리 표시)
ALTER TABLE commercial_stats
    MODIFY COLUMN foot_traffic_delta DECIMAL(7,4),
    MODIFY COLUMN store_count_delta DECIMAL(7,4),
    MODIFY COLUMN sales_delta DECIMAL(7,4),
    MODIFY COLUMN rent_delta DECIMAL(7,4),
    MODIFY COLUMN closure_rate DECIMAL(7,4),
    MODIFY COLUMN closure_rate_delta DECIMAL(7,4),
    MODIFY COLUMN vacancy_rate DECIMAL(7,4),
    MODIFY COLUMN vacancy_rate_delta DECIMAL(7,4);

-- 기존 데이터 삭제 (재적재 필요)
DELETE FROM commercial_stats;
