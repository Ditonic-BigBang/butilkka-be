-- 음식 관련 업종(CS100xxx) 10개만 유지, 나머지 삭제
-- FK 참조 먼저 정리
UPDATE users SET category_code = NULL WHERE category_code LIKE 'CS2%' OR category_code LIKE 'CS3%' OR category_code LIKE 'CS4%';
DELETE FROM stores WHERE category_code LIKE 'CS2%' OR category_code LIKE 'CS3%' OR category_code LIKE 'CS4%';

DELETE FROM categories WHERE category_code LIKE 'CS2%';
DELETE FROM categories WHERE category_code LIKE 'CS3%';
DELETE FROM categories WHERE category_code LIKE 'CS4%';
