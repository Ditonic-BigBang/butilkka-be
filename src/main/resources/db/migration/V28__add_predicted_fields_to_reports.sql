-- reports: AI 예측 필드 추가
ALTER TABLE reports
    ADD COLUMN predicted_trend VARCHAR(10) NULL COMMENT '다음 분기 예측 트렌드 (성장/유지/쇠퇴)' AFTER ai_outlook;

ALTER TABLE reports
    ADD COLUMN predicted_next_grade CHAR(1) NULL COMMENT '다음 분기 예측 등급 (A~E)' AFTER predicted_trend;
