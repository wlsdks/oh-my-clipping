-- status column + CHECK constraint
ALTER TABLE batch_categories ADD COLUMN status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE';
ALTER TABLE batch_categories ADD CONSTRAINT chk_category_status CHECK (status IN ('ACTIVE', 'PAUSED'));
UPDATE batch_categories SET status = 'PAUSED' WHERE is_active = FALSE;

-- pausedAt column
ALTER TABLE batch_categories ADD COLUMN paused_at TIMESTAMP;
UPDATE batch_categories SET paused_at = NOW() WHERE status = 'PAUSED';

CREATE INDEX IF NOT EXISTS idx_category_status ON batch_categories (status);
