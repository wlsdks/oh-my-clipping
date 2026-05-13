package com.ohmyclipping.mcp

import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.isRegularFile
import kotlin.io.path.name
import kotlin.io.path.readText

/**
 * 도구 이름 → `ToolAnnotations` 매핑이 의도한 분류를 만족하는지 검증한다.
 *
 * MCP 스펙의 `annotations` 는 클라이언트(LLM/orchestrator)에게 도구의
 * 안전성 힌트를 전달한다. 분류 실수(예: send_digest 를 readOnly 로 표기)는
 * 사용자가 의도치 않은 Slack 발송을 유발할 수 있으므로 중요한 가드레일이다.
 */
class McpToolAnnotationsTest {

    @Nested
    inner class `읽기 전용 도구` {

        @Test
        fun `user_list_categories 는 readOnly 이고 idempotent 이다`() {
            val ann = McpToolAnnotations.BY_NAME["user_list_categories"]
            ann shouldNotBe null
            ann!!.readOnlyHint() shouldBe true
            ann.destructiveHint() shouldBe false
            ann.idempotentHint() shouldBe true
        }

        @Test
        fun `admin_list_recent_jobs 는 readOnly 이고 idempotent 이다`() {
            val ann = McpToolAnnotations.BY_NAME["admin_list_recent_jobs"]
            ann shouldNotBe null
            ann!!.readOnlyHint() shouldBe true
            ann.idempotentHint() shouldBe true
        }

        @Test
        fun `admin_export 는 readOnly 로 표시된다 (DB 를 수정하지 않음)`() {
            val ann = McpToolAnnotations.BY_NAME["admin_export"]
            ann shouldNotBe null
            ann!!.readOnlyHint() shouldBe true
        }

        @Test
        fun `user_get_my_briefing 은 readOnly 이고 idempotent 이다`() {
            val ann = McpToolAnnotations.BY_NAME["user_get_my_briefing"]
            ann shouldNotBe null
            ann!!.readOnlyHint() shouldBe true
            ann.destructiveHint() shouldBe false
            ann.idempotentHint() shouldBe true
        }
    }

    @Nested
    inner class `파괴적 외부 액션` {

        @Test
        fun `admin_send_digest 는 destructive 이고 비멱등이다`() {
            val ann = McpToolAnnotations.BY_NAME["admin_send_digest"]
            ann shouldNotBe null
            ann!!.destructiveHint() shouldBe true
            ann.idempotentHint() shouldBe false
            ann.readOnlyHint() shouldBe false
        }
    }

    @Nested
    inner class `비멱등 쓰기 도구` {

        @Test
        fun `admin_collect 는 비멱등이고 destructive 가 아니다`() {
            val ann = McpToolAnnotations.BY_NAME["admin_collect"]
            ann shouldNotBe null
            ann!!.idempotentHint() shouldBe false
            ann.destructiveHint() shouldBe false
            ann.readOnlyHint() shouldBe false
        }

        @Test
        fun `admin_pipeline 과 admin_summarize 도 비멱등이다`() {
            listOf("admin_pipeline", "admin_summarize", "admin_daily_summary").forEach { name ->
                val ann = McpToolAnnotations.BY_NAME[name]
                ann shouldNotBe null
                ann!!.idempotentHint() shouldBe false
            }
        }
    }

    @Nested
    inner class `매핑 완전성` {

        @Test
        fun `주요 admin 및 user 도구가 모두 매핑돼 있다`() {
            val expected = setOf(
                "admin_collect",
                "admin_summarize",
                "admin_daily_summary",
                "admin_pipeline",
                "admin_collect_async",
                "admin_summarize_async",
                "admin_send_digest",
                "admin_export",
                "admin_list_categories",
                "admin_list_recent_jobs",
                "admin_list_failing_sources",
                "admin_list_pending_requests",
                "admin_job_status",
                "admin_approve_pending_request",
                "admin_cost_summary",
                "admin_slack_channel_diagnose",
                "user_list_categories",
                "user_list_sources",
                "user_list_recent_summaries",
                "user_list_top_summaries",
                "user_search_summaries",
                "user_get_summary_detail",
                "user_get_original_preview",
                "user_get_trending_keywords",
                "user_get_category_overview",
                "user_get_my_briefing",
            )
            val missing = expected - McpToolAnnotations.BY_NAME.keys
            missing shouldBe emptySet()
        }

        @Test
        fun `모든 MCP 도구는 정확히 하나의 annotation 매핑을 가진다`() {
            val toolNames = discoverMcpToolNames()

            (toolNames - McpToolAnnotations.BY_NAME.keys) shouldBe emptySet()
            (McpToolAnnotations.BY_NAME.keys - toolNames) shouldBe emptySet()
        }
    }

    private fun discoverMcpToolNames(): Set<String> {
        val sourceRoots = listOf(
            Path.of("src/main/kotlin/com/ohmyclipping/admin/mcp"),
            Path.of("src/main/kotlin/com/ohmyclipping/user/mcp"),
        )
        val toolNamePattern = Regex("""@Tool\s*\([\s\S]*?\)\s*fun\s+([A-Za-z0-9_]+)\s*\(""")

        return sourceRoots
            .flatMap { root ->
                Files.walk(root).use { paths ->
                    paths
                        .filter { it.isRegularFile() && it.name.endsWith(".kt") }
                        .map { it.readText() }
                        .toList()
                }
            }
            .flatMap { source ->
                toolNamePattern.findAll(source).map { it.groupValues[1] }
            }
            .toSet()
    }
}
