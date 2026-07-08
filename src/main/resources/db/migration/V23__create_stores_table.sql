-- stores 테이블 생성 (다점포 지원)
CREATE TABLE stores (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    store_name VARCHAR(50) NOT NULL,
    store_address VARCHAR(100),
    store_open_date DATE,
    region_code VARCHAR(20) NOT NULL,
    category_code VARCHAR(30) NOT NULL,
    lat DOUBLE,
    lng DOUBLE,
    is_primary BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_stores_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT fk_stores_region FOREIGN KEY (region_code) REFERENCES regions(region_code),
    CONSTRAINT fk_stores_category FOREIGN KEY (category_code) REFERENCES categories(category_code)
);

-- 인덱스
CREATE INDEX idx_stores_user_id ON stores(user_id);
CREATE INDEX idx_stores_is_primary ON stores(user_id, is_primary);
