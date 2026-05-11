-- batch_summaries.created_at 내림차순 인덱스 (날짜 범위 조회 가속)
CREATE INDEX IF NOT EXISTS idx_batch_summaries_created ON batch_summaries(created_at DESC);

-- clipping_review_items.reviewed_at 인덱스 (리뷰 기록 조회 가속)
CREATE INDEX IF NOT EXISTS idx_review_items_reviewed_at ON clipping_review_items(reviewed_at);

-- admin_users 역할+승인상태 복합 인덱스 (목록 조회 가속)
CREATE INDEX IF NOT EXISTS idx_admin_users_role_status ON admin_users(role, approval_status);
