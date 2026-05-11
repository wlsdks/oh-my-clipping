-- 구독별 개별 발송 시간 설정 컬럼 추가
-- NULL이면 글로벌 user_delivery_schedules 설정을 사용한다.
ALTER TABLE clipping_category_rules ADD COLUMN delivery_days VARCHAR(50) DEFAULT NULL;
ALTER TABLE clipping_category_rules ADD COLUMN delivery_hour INT DEFAULT NULL;
ALTER TABLE clipping_category_rules ADD COLUMN delivery_preset VARCHAR(20) DEFAULT NULL;
