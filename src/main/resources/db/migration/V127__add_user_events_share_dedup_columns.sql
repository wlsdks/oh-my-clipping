-- Phase 3 PR3b: Slack link_shared passive capture dedup 용 전용 컬럼.
-- JSON 은 UNIQUE 불가 → 전용 컬럼 + UNIQUE index.
ALTER TABLE user_events ADD COLUMN target_channel_id VARCHAR(64);
ALTER TABLE user_events ADD COLUMN slack_message_ts VARCHAR(64);

-- 같은 (summary_id, target_channel_id, slack_message_ts) 중복 저장 방지.
-- share 이벤트 아닌 row 는 세 컬럼 중 일부 또는 전부가 NULL 이므로 SQL 표준상 NULL 끼리는 서로 다름 → 제약 유효 범위 밖 (H2, PostgreSQL 모두 동일 동작).
CREATE UNIQUE INDEX ux_user_events_share_dedup
  ON user_events (summary_id, target_channel_id, slack_message_ts);
