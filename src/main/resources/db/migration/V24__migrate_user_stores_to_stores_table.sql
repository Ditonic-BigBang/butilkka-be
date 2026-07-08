-- 기존 users 테이블의 가게 정보를 stores 테이블로 마이그레이션
INSERT INTO stores (user_id, store_name, store_address, store_open_date, region_code, category_code, lat, lng, is_primary)
SELECT
    id,
    store_name,
    store_address,
    store_open_date,
    store_region,
    category_code,
    store_lat,
    store_lng,
    TRUE
FROM users
WHERE store_region IS NOT NULL AND category_code IS NOT NULL AND store_name IS NOT NULL;
