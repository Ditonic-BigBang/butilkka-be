ALTER TABLE users
    ADD COLUMN store_lat DECIMAL(10, 7) NULL COMMENT '가게 위도' AFTER store_region,
    ADD COLUMN store_lng DECIMAL(10, 7) NULL COMMENT '가게 경도' AFTER store_lat;
