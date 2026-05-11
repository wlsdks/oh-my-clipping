-- Add claimed_at to delivery_log for retry claim tracking
ALTER TABLE delivery_log ADD COLUMN IF NOT EXISTS claimed_at TIMESTAMP NULL;
