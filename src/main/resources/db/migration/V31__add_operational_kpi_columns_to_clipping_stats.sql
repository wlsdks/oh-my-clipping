ALTER TABLE clipping_stats ADD COLUMN items_duplicates INT NOT NULL DEFAULT 0;
ALTER TABLE clipping_stats ADD COLUMN slack_send_attempts INT NOT NULL DEFAULT 0;
ALTER TABLE clipping_stats ADD COLUMN slack_send_successes INT NOT NULL DEFAULT 0;

CREATE INDEX idx_clipping_stats_date ON clipping_stats(stat_date);
