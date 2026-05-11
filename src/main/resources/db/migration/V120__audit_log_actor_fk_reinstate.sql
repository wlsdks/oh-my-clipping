-- ============================================================
-- V120: audit_log.actor_id FK 재추가 (B 리팩터 완료)
--
-- V117 에서 걸었던 `fk_audit_log_actor` 는 V119 에서 일시 drop 되어 있었다
-- (이유: 컨트롤러/서비스가 username 을 actor_id 에 그대로 저장해 FK 위반).
-- 이제 `AuditActorResolver` 가 principal 을 `admin_users.id` 로 변환해 저장
-- 측에 항상 UUID 또는 NULL 만 내려주므로 FK 를 되살릴 수 있다.
--
-- 안전 장치:
--   1. 기존 row 중 actor_id 가 admin_users 에 존재하지 않는 값 (예: V119 이후
--      쌓인 username 문자열) 은 NULL 로 정규화한다. actor_name 은 유지되어
--      식별 정보 손실이 최소화된다.
--   2. `DROP CONSTRAINT IF EXISTS` 선행으로 재실행 안전.
--   3. `ON DELETE SET NULL` — admin 탈퇴 시 감사 row 는 보존.
--
-- 이 migration 이후 task #8 은 close 된다.
-- ============================================================

-- 1) 기존 row 정규화: admin_users 에 없는 actor_id 를 NULL 로.
UPDATE audit_log SET actor_id = NULL
 WHERE actor_id IS NOT NULL AND actor_id NOT IN (SELECT id FROM admin_users);

-- 2) FK 재추가 (idempotent).
ALTER TABLE audit_log DROP CONSTRAINT IF EXISTS fk_audit_log_actor;
ALTER TABLE audit_log ADD CONSTRAINT fk_audit_log_actor
  FOREIGN KEY (actor_id) REFERENCES admin_users(id) ON DELETE SET NULL;
