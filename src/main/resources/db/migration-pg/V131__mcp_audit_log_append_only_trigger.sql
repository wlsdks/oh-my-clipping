-- mcp_audit_log append-only enforcement (PostgreSQL only).
--
-- V99 주석에 "append-only 트리거는 V93b 에서 분리" 라고 적혀 있지만
-- 실제로는 작성되지 않았다. 이 마이그레이션으로 UPDATE/DELETE 를 DB 레벨에서 차단한다.
-- DataCleanupScheduler 가 주기적으로 삭제할 때는 세션 변수
-- mcp.cleanup_context='true' 를 세팅한 뒤 동일 트랜잭션에서 DELETE 한다.
--
-- H2 테스트 환경에서는 flyway.locations 가 db/migration 만 포함하므로
-- 이 파일은 실행되지 않는다. 따라서 H2 통합 테스트는 append-only 를
-- 강제하지 않으며, 운영(PostgreSQL) 전용 제약이다.

CREATE OR REPLACE FUNCTION mcp_audit_log_prevent_modification()
RETURNS TRIGGER AS $$
BEGIN
  -- cleanup_context 세션 변수가 명시적으로 'true' 인 트랜잭션에서만 수정을 허용한다.
  -- 기본값은 NULL 로 current_setting(name, true) 은 NULL 을 돌려주어 IS NOT DISTINCT
  -- FROM 'true' 가 false 로 평가되고 RAISE EXCEPTION 으로 진행된다.
  IF current_setting('mcp.cleanup_context', true) IS NOT DISTINCT FROM 'true' THEN
    RETURN COALESCE(OLD, NEW);
  END IF;
  RAISE EXCEPTION 'mcp_audit_log is append-only (set mcp.cleanup_context=true to bypass)';
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS mcp_audit_log_no_update ON mcp_audit_log;
CREATE TRIGGER mcp_audit_log_no_update
  BEFORE UPDATE OR DELETE ON mcp_audit_log
  FOR EACH ROW
  EXECUTE FUNCTION mcp_audit_log_prevent_modification();
