-- Slack 멤버 ID(U...)와 DM 채널 ID(D...)를 분리한다.
ALTER TABLE admin_users ADD COLUMN slack_member_id VARCHAR(20);

-- 기존에 U...가 slack_dm_channel_id에 저장된 경우 -> slack_member_id로 이동
UPDATE admin_users
SET slack_member_id = slack_dm_channel_id, slack_dm_channel_id = NULL
WHERE slack_dm_channel_id LIKE 'U%';
