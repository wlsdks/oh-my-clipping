-- MCP 감사 로그 테이블: 도구 호출 기록을 append-only로 보관한다.
CREATE TABLE mcp_audit_log (
    id               VARCHAR(36)  PRIMARY KEY,
    request_id       VARCHAR(36)  NOT NULL,
    actor            VARCHAR(80)  NOT NULL,
    tool_name        VARCHAR(120) NOT NULL,
    args_json        TEXT,
    result_status    VARCHAR(20)  NOT NULL,
    result_code      INT,
    http_status_code INT,
    duration_ms      INT          NOT NULL,
    error_message    TEXT,
    created_at       TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_mcp_audit_log_created     ON mcp_audit_log(created_at DESC);
CREATE INDEX idx_mcp_audit_log_tool        ON mcp_audit_log(tool_name, created_at DESC);
CREATE INDEX idx_mcp_audit_log_created_asc ON mcp_audit_log(created_at ASC);
CREATE INDEX idx_mcp_audit_log_request     ON mcp_audit_log(request_id);

-- PostgreSQL append-only 보호 트리거는 운영 전용 migration 으로 분리한다.
-- 실제 트리거 정의: db/migration-pg/V128__mcp_audit_log_append_only_trigger.sql
-- H2 테스트 환경에서는 트리거 없이 동작하며, 운영(PostgreSQL)에서만 append-only가 강제된다.
