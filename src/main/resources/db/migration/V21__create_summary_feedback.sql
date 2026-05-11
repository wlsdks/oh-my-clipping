CREATE TABLE summary_feedback (
    id VARCHAR(36) PRIMARY KEY,
    summary_id VARCHAR(36) NOT NULL REFERENCES batch_summaries(id) ON DELETE CASCADE,
    feedback_type VARCHAR(20) NOT NULL CHECK (feedback_type IN ('LIKE', 'NEUTRAL', 'DISLIKE')),
    user_id VARCHAR(120) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE UNIQUE INDEX ux_summary_feedback_summary_user
    ON summary_feedback (summary_id, user_id);

CREATE INDEX idx_summary_feedback_created_at
    ON summary_feedback (created_at DESC);

CREATE INDEX idx_summary_feedback_type_created_at
    ON summary_feedback (feedback_type, created_at DESC);
