-- 유동인구 CSV 업데이트로 인한 district_stats 재적재
-- 서버 시작 시 DataLoadService가 자동으로 새 CSV 데이터를 로드함
TRUNCATE TABLE district_stats;
