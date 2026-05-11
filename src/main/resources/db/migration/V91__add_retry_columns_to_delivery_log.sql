-- Add missing retry infrastructure columns to delivery_log
ALTER TABLE delivery_log ADD COLUMN IF NOT EXISTS retry_count INTEGER NOT NULL DEFAULT 0;
ALTER TABLE delivery_log ADD COLUMN IF NOT EXISTS next_retry_at TIMESTAMP NULL;
ALTER TABLE delivery_log ADD COLUMN IF NOT EXISTS last_error VARCHAR(500) NULL;

-- Update status check constraint to include RETRYING
ALTER TABLE delivery_log DROP CONSTRAINT IF EXISTS chk_delivery_log_status;
ALTER TABLE delivery_log ADD CONSTRAINT chk_delivery_log_status
    CHECK (status IN ('RESERVED', 'SENT', 'SKIPPED', 'FAILED', 'FINALIZATION_FAILED', 'RETRYING'));
