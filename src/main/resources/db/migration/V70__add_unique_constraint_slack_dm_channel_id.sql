-- V70__add_unique_constraint_slack_dm_channel_id.sql
-- Slack Member ID 중복 등록을 방지한다.
-- 동일 Slack 계정을 여러 시스템 계정에 연결하는 것은 스푸핑 위험이 있다.

-- 기존 중복 데이터가 있으면 최신 행만 남기고 나머지를 NULL로 초기화한다.
UPDATE admin_users SET slack_dm_channel_id = NULL
WHERE slack_dm_channel_id IS NOT NULL
  AND id NOT IN (
    SELECT id FROM (
      SELECT id, ROW_NUMBER() OVER (PARTITION BY slack_dm_channel_id ORDER BY updated_at DESC) AS rn
      FROM admin_users
      WHERE slack_dm_channel_id IS NOT NULL
    ) ranked WHERE rn = 1
  );

ALTER TABLE admin_users
    ADD CONSTRAINT uq_admin_users_slack_dm_channel_id UNIQUE (slack_dm_channel_id);
