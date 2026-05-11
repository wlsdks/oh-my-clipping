-- Allow silent-period notification records created by SlackDigestWorker.
-- Without this status the DM can be sent successfully, but updating delivery_log
-- to NOTIFIED_NO_CONTENT fails on the status CHECK constraint.
ALTER TABLE delivery_log DROP CONSTRAINT IF EXISTS chk_delivery_log_status;
ALTER TABLE delivery_log ADD CONSTRAINT chk_delivery_log_status
    CHECK (status IN (
        'RESERVED',
        'SENT',
        'SKIPPED',
        'FAILED',
        'FINALIZATION_FAILED',
        'RETRYING',
        'ABANDONED',
        'STALE',
        'NOTIFIED_NO_CONTENT'
    ));
