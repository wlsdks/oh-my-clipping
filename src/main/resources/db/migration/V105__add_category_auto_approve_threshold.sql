-- V105__add_category_auto_approve_threshold.sql
-- AI INCLUDE 제안을 자동 승인할 importance 임계값을 카테고리별로 저장한다.
-- NULL = 비활성(기본). 값이 있으면 해당 importance 이상의 INCLUDE 제안이 즉시 INCLUDE로 저장된다.

ALTER TABLE clipping_category_rules
    ADD COLUMN auto_approve_threshold DOUBLE PRECISION;

-- 범위는 [0,1]. NULL은 비활성을 의미하므로 허용한다.
ALTER TABLE clipping_category_rules
    ADD CONSTRAINT chk_category_rules_auto_approve_threshold
        CHECK (auto_approve_threshold IS NULL OR (auto_approve_threshold >= 0 AND auto_approve_threshold <= 1));
