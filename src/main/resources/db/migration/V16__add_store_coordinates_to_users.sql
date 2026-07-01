ALTER TABLE users
    ADD COLUMN store_lat DOUBLE NULL COMMENT '가게 위도' AFTER store_region,
    ADD COLUMN store_lng DOUBLE NULL COMMENT '가게 경도' AFTER store_lat;
