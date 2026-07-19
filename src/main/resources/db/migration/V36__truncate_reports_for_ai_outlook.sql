-- 리포트 관련 테이블 초기화 (ai_outlook 등 새 필드 적용을 위해)
-- FK 제약으로 TRUNCATE 불가하여 DELETE 사용

DELETE FROM report_decision_reasons;
DELETE FROM report_alternative_regions;
DELETE FROM report_similar_cases;
DELETE FROM report_signals;
DELETE FROM report_causes;
DELETE FROM reports;
