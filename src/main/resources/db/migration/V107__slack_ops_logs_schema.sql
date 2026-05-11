-- Paired with db/migration-pg/V108__slack_ops_logs_indexes.sql for PG-only CONCURRENTLY indexes.
-- V107__slack_ops_logs_schema.sql
-- 파이프라인 run에 Slack 스레드 연결 (H2 호환: ADD COLUMN 각각 분리)
ALTER TABLE pipeline_runs ADD COLUMN slack_thread_ts VARCHAR(64);
ALTER TABLE pipeline_runs ADD COLUMN slack_payload_json TEXT;

-- 월간 예산 알림 dedup (재시작 후에도 유지)
CREATE TABLE cost_alert_notifications (
  month_id        CHAR(7)      NOT NULL,   -- "2026-04"
  threshold_level VARCHAR(32)  NOT NULL,   -- WARN_90 | CRITICAL_100 | DAILY_KRW_30000
  notified_at     TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (month_id, threshold_level)
);
