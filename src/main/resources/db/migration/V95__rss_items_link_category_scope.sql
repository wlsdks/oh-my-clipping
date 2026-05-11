-- V95__rss_items_link_category_scope.sql
-- rss_items.link 유니크 제약을 글로벌 → 카테고리 스코프로 변경한다.
-- 같은 기사 URL이 카테고리별로 독립 저장 가능해진다.

ALTER TABLE rss_items DROP CONSTRAINT IF EXISTS rss_items_link_key;
DROP INDEX IF EXISTS idx_rss_items_link;
CREATE UNIQUE INDEX uq_rss_items_link_category ON rss_items(link, category_id);
