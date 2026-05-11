-- V109__cost_alert_notifications_month_id_varchar.sql
-- V107 created cost_alert_notifications.month_id as CHAR(7). Hibernate validates
-- columns against Types#VARCHAR for String @Column(length = N), so boot fails with
-- "found [bpchar], but expecting [char(7) (Types#VARCHAR)]". Switching to VARCHAR(7)
-- preserves length semantics and matches the entity mapping.
-- ANSI SET DATA TYPE — accepted by both PostgreSQL and H2.

ALTER TABLE cost_alert_notifications
  ALTER COLUMN month_id SET DATA TYPE VARCHAR(7);
