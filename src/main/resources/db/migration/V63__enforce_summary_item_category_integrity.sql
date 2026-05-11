UPDATE batch_summaries
SET category_id = (
    SELECT rss_items.category_id
    FROM rss_items
    WHERE rss_items.id = batch_summaries.rss_item_id
)
WHERE EXISTS (
    SELECT 1
    FROM rss_items
    WHERE rss_items.id = batch_summaries.rss_item_id
)
AND category_id <> (
    SELECT rss_items.category_id
    FROM rss_items
    WHERE rss_items.id = batch_summaries.rss_item_id
);

ALTER TABLE rss_items
    ADD CONSTRAINT uq_rss_items_id_category
    UNIQUE (id, category_id);

ALTER TABLE batch_summaries
    ADD CONSTRAINT fk_batch_summaries_rss_item_category
    FOREIGN KEY (rss_item_id, category_id)
    REFERENCES rss_items(id, category_id);
