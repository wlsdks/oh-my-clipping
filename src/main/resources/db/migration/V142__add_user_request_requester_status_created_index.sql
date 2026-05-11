CREATE INDEX IF NOT EXISTS idx_clipping_user_requests_requester_status_created
    ON clipping_user_requests(requester_user_id, status, created_at DESC);
