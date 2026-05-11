-- V98__summary_cache_add_translated_title.sql
-- 외국어 기사의 번역된 제목을 캐시에 보존하여 카테고리간 캐시 재사용 시 데이터 손실 방지.

ALTER TABLE summary_cache ADD COLUMN translated_title TEXT;
