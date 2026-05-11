-- V96__original_contents_link_scope.sql
-- original_contents.source_link 유니크 제약을 (source_link, rss_item_id) 복합으로 변경한다.

ALTER TABLE original_contents DROP CONSTRAINT IF EXISTS original_contents_source_link_key;
DROP INDEX IF EXISTS idx_original_contents_source_link;
CREATE UNIQUE INDEX uq_original_contents_link_item ON original_contents(source_link, rss_item_id);
