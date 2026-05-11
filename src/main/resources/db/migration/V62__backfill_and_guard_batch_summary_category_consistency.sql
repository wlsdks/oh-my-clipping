INSERT INTO rss_items (
    id, title, content, link, language, is_processed, category_id, rss_source_id, created_at
)
SELECT
    batch_summaries.id,
    batch_summaries.original_title,
    batch_summaries.summary,
    batch_summaries.source_link,
    CASE
        WHEN batch_summaries.translated_title IS NULL THEN 'KOREAN'
        ELSE 'FOREIGN'
    END,
    TRUE,
    batch_summaries.category_id,
    NULL,
    batch_summaries.created_at
FROM batch_summaries
JOIN rss_items current_item ON current_item.id = batch_summaries.rss_item_id
LEFT JOIN rss_items same_link_item ON same_link_item.link = batch_summaries.source_link
WHERE (
    current_item.title <> batch_summaries.original_title
    OR current_item.link <> batch_summaries.source_link
    OR current_item.category_id <> batch_summaries.category_id
)
AND same_link_item.id IS NULL;

UPDATE batch_summaries
SET rss_item_id = (
        SELECT rss_items.id
        FROM rss_items
        WHERE rss_items.link = batch_summaries.source_link
    ),
    category_id = (
        SELECT rss_items.category_id
        FROM rss_items
        WHERE rss_items.link = batch_summaries.source_link
    )
WHERE EXISTS (
    SELECT 1
    FROM rss_items
    WHERE rss_items.link = batch_summaries.source_link
)
AND (
    rss_item_id <> (
        SELECT rss_items.id
        FROM rss_items
        WHERE rss_items.link = batch_summaries.source_link
    )
    OR category_id <> (
        SELECT rss_items.category_id
        FROM rss_items
        WHERE rss_items.link = batch_summaries.source_link
    )
);

CREATE INDEX idx_batch_summaries_rss_item_category
    ON batch_summaries (rss_item_id, category_id);
