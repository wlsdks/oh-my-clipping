-- rss_items.rss_source_id: FK는 있으나 인덱스 누락
CREATE INDEX IF NOT EXISTS idx_rss_items_rss_source_id ON rss_items(rss_source_id);

-- batch_summaries.rss_item_id: FK는 있으나 인덱스 누락
CREATE INDEX IF NOT EXISTS idx_batch_summaries_rss_item_id ON batch_summaries(rss_item_id);

-- delivery_log.category_id FK는 운영 DB에서 수동 적용 권장:
-- ALTER TABLE delivery_log ADD CONSTRAINT fk_delivery_log_category
--     FOREIGN KEY (category_id) REFERENCES batch_categories(id) ON DELETE CASCADE;
-- H2 테스트 환경에서는 기존 테스트 데이터와 FK 제약이 충돌하므로 자동 마이그레이션에서 제외.
