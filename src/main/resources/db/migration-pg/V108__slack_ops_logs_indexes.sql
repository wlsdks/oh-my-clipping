-- V108__slack_ops_logs_indexes.sql
-- Paired with db/migration/V107__slack_ops_logs_schema.sql.
-- PostgreSQL only. Skipped in H2 test environment via flyway.locations split.
--
-- Note: CONCURRENTLY was considered but dropped because Flyway 11 opens a
-- schema-history session that CONCURRENTLY waits on (virtualxid lock). For the
-- typical pipeline_runs size (thousands of rows, not millions), plain CREATE
-- INDEX is fast enough and simpler. If future scale requires online index
-- builds, add them as a separate PG-only migration with executeInTransaction=false
-- and split each CREATE INDEX CONCURRENTLY into its own file.

CREATE INDEX IF NOT EXISTS idx_pipeline_runs_status_started
  ON pipeline_runs (status, started_at DESC);

CREATE INDEX IF NOT EXISTS idx_pipeline_runs_category_status_ended
  ON pipeline_runs (category_id, status, ended_at DESC);
