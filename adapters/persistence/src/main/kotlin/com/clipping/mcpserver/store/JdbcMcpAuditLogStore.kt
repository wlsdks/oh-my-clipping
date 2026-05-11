package com.clipping.mcpserver.store

import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import java.sql.Timestamp
import java.time.Instant

private val log = KotlinLogging.logger {}

/**
 * MCP 감사 로그를 JDBC로 관리하는 저장소 구현체.
 *
 * append-only 테이블에 INSERT하고, 보존 기간 만료 시에는 PostgreSQL 트리거
 * (V128 migration) 를 우회하기 위해 `mcp.cleanup_context='true'` 세션 변수를
 * 세팅한 뒤 삭제한다. H2 테스트 환경에는 트리거 자체가 없으므로 SET 구문이
 * 실패하더라도 무시한다.
 */
@Repository
class JdbcMcpAuditLogStore(private val jdbc: JdbcTemplate) : McpAuditLogStore {

    override fun insert(entry: McpAuditEntry) {
        // 감사 로그 INSERT — created_at은 DB 기본값 사용
        jdbc.update(
            """
            INSERT INTO mcp_audit_log
                (id, request_id, actor, tool_name, args_json,
                 result_status, result_code, http_status_code,
                 duration_ms, error_message)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """.trimIndent(),
            entry.id,
            entry.requestId,
            entry.actor,
            entry.toolName,
            entry.argsJson,
            entry.resultStatus,
            entry.resultCode,
            entry.httpStatusCode,
            entry.durationMs,
            entry.errorMessage,
        )
    }

    /**
     * 보존 기간이 지난 레코드를 삭제한다.
     *
     * `SET LOCAL` 은 현재 트랜잭션 안에서만 유효하므로, append-only 트리거를
     * 안전하게 우회하려면 SET + DELETE 를 **반드시 동일 트랜잭션** 에서
     * 실행해야 한다. 따라서 이 메서드에 `@Transactional(REQUIRES_NEW)` 을
     * 명시해 상위 호출부(스케줄러 등)의 트랜잭션 상태와 무관하게 단일
     * 트랜잭션을 강제한다.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    override fun deleteOlderThan(cutoff: Instant): Int {
        // PostgreSQL append-only 트리거를 우회하기 위해 세션 변수를 설정한다.
        // H2에서는 SET 구문이 무시되어도 트리거 자체가 없으므로 문제없다.
        enableCleanupContext()
        val deleted = jdbc.update(
            "DELETE FROM mcp_audit_log WHERE created_at < ?",
            Timestamp.from(cutoff),
        )
        if (deleted > 0) {
            log.info { "MCP 감사 로그 $deleted 건 삭제 (cutoff=$cutoff)" }
        }
        return deleted
    }

    /**
     * PostgreSQL 세션 변수 mcp.cleanup_context를 true로 설정한다.
     * SET LOCAL 은 현재 트랜잭션 커밋/롤백 시점까지만 유효하며, 명시적인
     * 해제 호출(`SET LOCAL ... 'false'`) 이 필요하지 않다.
     */
    private fun enableCleanupContext() {
        try {
            jdbc.execute("SET LOCAL mcp.cleanup_context = 'true'")
        } catch (_: Exception) {
            // H2 등 SET LOCAL 미지원 DB에서는 무시한다 — 트리거 자체가 없다.
            log.debug { "SET LOCAL mcp.cleanup_context 미지원 — 건너뜀" }
        }
    }
}
