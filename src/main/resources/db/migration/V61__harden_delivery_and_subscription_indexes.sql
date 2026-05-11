UPDATE delivery_log
SET retry_attempted = FALSE
WHERE retry_attempted IS NULL;

ALTER TABLE delivery_log
    ALTER COLUMN retry_attempted SET DEFAULT FALSE;

ALTER TABLE delivery_log
    ALTER COLUMN retry_attempted SET NOT NULL;

ALTER TABLE delivery_log
    ADD CONSTRAINT chk_delivery_log_status
        CHECK (status IN ('RESERVED', 'SENT', 'SKIPPED', 'FAILED', 'FINALIZATION_FAILED'));

CREATE INDEX idx_delivery_log_retry_created
    ON delivery_log(retry_attempted, status, created_at);

CREATE INDEX idx_delivery_log_category_created
    ON delivery_log(category_id, created_at DESC);

CREATE INDEX idx_delivery_log_category_status_date_created
    ON delivery_log(category_id, status, delivery_date, created_at DESC);

CREATE INDEX idx_clipping_user_requests_requester_category_status
    ON clipping_user_requests(requester_user_id, approved_category_id, status);

CREATE INDEX idx_rss_items_category_processed_created
    ON rss_items(category_id, is_processed, created_at);

CREATE INDEX idx_rss_sources_collect_ready
    ON rss_sources(category_id, crawl_approved, is_active, summary_allowed, verification_status, created_at);

ALTER TABLE user_delivery_schedules
    ADD CONSTRAINT chk_user_delivery_schedules_hour
        CHECK (delivery_hour BETWEEN 0 AND 23);

ALTER TABLE user_delivery_schedules
    ADD CONSTRAINT chk_user_delivery_schedules_preset
        CHECK (preset IN ('WEEKDAYS', 'EVERYDAY', 'CUSTOM'));

CREATE INDEX idx_user_delivery_schedules_hour_updated
    ON user_delivery_schedules(delivery_hour, updated_at);

CREATE INDEX idx_user_delivery_schedules_updated_at
    ON user_delivery_schedules(updated_at);
