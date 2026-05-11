ALTER TABLE batch_summaries ADD COLUMN sentiment VARCHAR(20);
ALTER TABLE batch_summaries ADD COLUMN event_type VARCHAR(30);

CREATE INDEX idx_batch_summaries_sentiment ON batch_summaries(sentiment);
CREATE INDEX idx_batch_summaries_event_type ON batch_summaries(event_type);
CREATE INDEX idx_batch_summaries_created_at ON batch_summaries(created_at);
