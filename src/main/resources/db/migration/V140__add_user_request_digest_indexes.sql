-- Slack 다이제스트 fan-out 대상 조회 최적화.
-- status + approved_category_id 조건과 최신순 정렬을 함께 타도록 구성한다.
CREATE INDEX IF NOT EXISTS idx_clipping_user_requests_status_category_created
    ON clipping_user_requests(status, approved_category_id, created_at DESC);
