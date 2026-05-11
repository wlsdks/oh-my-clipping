-- V132__add_exclude_event_types_to_category_rules.sql
-- PR-3-lite: 카테고리별 자동 EXCLUDE 룰을 위한 event_type 블랙리스트 컬럼.
-- 기본값 '[]'(룰 비활성)이며 Jackson 으로 List<String> 파싱한다.
-- 동일 테이블의 include_keywords / exclude_keywords / risk_tags 컬럼과 동일한 TEXT JSON 포맷을 따른다.

ALTER TABLE clipping_category_rules
    ADD COLUMN exclude_event_types TEXT NOT NULL DEFAULT '[]';
