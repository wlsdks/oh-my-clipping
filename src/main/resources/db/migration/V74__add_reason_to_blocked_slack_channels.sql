-- Add reason column to blocked_slack_channels for admin context
-- Free text memo, optional, max 200 chars (enforced at controller level)
ALTER TABLE blocked_slack_channels ADD COLUMN reason TEXT NULL;
