-- V104__add_suggested_status_and_indexes.sql
-- Persist AI suggestion at review-item creation time for accuracy tracking.

ALTER TABLE clipping_review_items
ADD COLUMN suggested_status VARCHAR(20);

ALTER TABLE clipping_review_items
ADD CONSTRAINT chk_review_items_suggested_status
    CHECK (suggested_status IS NULL OR suggested_status IN ('INCLUDE', 'REVIEW', 'EXCLUDE'));

-- Composite index for review-stats queries (period filtering + category grouping).
-- H2 호환을 위해 partial index(WHERE절) 대신 일반 composite index 사용.
CREATE INDEX idx_review_items_reviewed_at_category
    ON clipping_review_items (reviewed_at, category_id, status, suggested_status);

-- Index for summary API (count by category + status + suggested_status)
CREATE INDEX idx_review_items_category_status
    ON clipping_review_items (category_id, status, suggested_status);
