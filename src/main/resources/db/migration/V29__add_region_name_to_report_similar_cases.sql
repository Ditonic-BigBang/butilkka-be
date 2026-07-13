-- Add region_name column to report_similar_cases for curated case display
ALTER TABLE report_similar_cases
ADD COLUMN region_name VARCHAR(50) AFTER region_code;
