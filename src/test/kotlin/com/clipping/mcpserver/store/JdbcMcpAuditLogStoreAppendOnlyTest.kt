package com.clipping.mcpserver.store

import io.kotest.matchers.comparables.shouldBeGreaterThan
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.ActiveProfiles
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID

/**
 * MCP 감사 로그 저장소의 **insert / deleteOlderThan** 해피패스를 검증한다.
 *
 * **Append-only 트리거는 PostgreSQL 전용**이다 (migration-pg/V128 참고).
 * H2 테스트 환경에는 `mcp_audit_log_prevent_modification()` 트리거가 설치되지
 * 않으므로 UPDATE/DELETE 거부는 이 테스트에서 검증하지 않는다. 운영(PG) 에서는
 * 정상 경로에 대해 `SET LOCAL mcp.cleanup_context='true'` 를 통해 삭제가 허용되는데,
 * 이 테스트는 해당 SET 구문이 H2 에서 무시되어도 DELETE 가 동작하는지 확인한다.
 */
@SpringBootTest
@ActiveProfiles("test")
class JdbcMcpAuditLogStoreAppendOnlyTest {

    @Autowired lateinit var store: McpAuditLogStore
    @Autowired lateinit var jdbc: JdbcTemplate

    @Test
    fun `insert 후 deleteOlderThan 은 cutoff 이전 레코드를 삭제한다`() {
        val oldId = UUID.randomUUID().toString()
        val newId = UUID.randomUUID().toString()

        // 신규 레코드 INSERT
        store.insert(sampleEntry(id = oldId))
        store.insert(sampleEntry(id = newId))

        // 한 건을 임의로 과거 시점으로 덮어 쓴다.
        jdbc.update(
            "UPDATE mcp_audit_log SET created_at = ? WHERE id = ?",
            java.sql.Timestamp.from(Instant.now().minus(120, ChronoUnit.DAYS)),
            oldId,
        )

        val cutoff = Instant.now().minus(90, ChronoUnit.DAYS)
        val deleted = store.deleteOlderThan(cutoff)

        deleted shouldBeGreaterThan 0
        // 신규 레코드는 남아있어야 한다.
        val remaining = jdbc.queryForObject(
            "SELECT COUNT(*) FROM mcp_audit_log WHERE id = ?",
            Int::class.java,
            newId,
        )
        remaining shouldBe 1
    }

    @Test
    fun `cutoff 보다 최근 레코드는 유지된다`() {
        val id = UUID.randomUUID().toString()
        store.insert(sampleEntry(id = id))

        // 30일 전 cutoff — 방금 INSERT 한 레코드는 해당되지 않아야 한다.
        val cutoff = Instant.now().minus(30, ChronoUnit.DAYS)
        store.deleteOlderThan(cutoff)

        val remaining = jdbc.queryForObject(
            "SELECT COUNT(*) FROM mcp_audit_log WHERE id = ?",
            Int::class.java,
            id,
        )
        remaining shouldBe 1
    }

    private fun sampleEntry(id: String = UUID.randomUUID().toString()) = McpAuditEntry(
        id = id,
        requestId = UUID.randomUUID().toString(),
        actor = "test-actor",
        toolName = "test_tool",
        argsJson = """{"k":"v"}""",
        resultStatus = "OK",
        resultCode = 0,
        httpStatusCode = 200,
        durationMs = 42,
        errorMessage = null,
    )
}
