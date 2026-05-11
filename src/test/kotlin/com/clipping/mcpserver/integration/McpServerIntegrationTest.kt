package com.clipping.mcpserver.integration

import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import org.junit.jupiter.api.Test
import org.springframework.ai.tool.ToolCallbackProvider
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles

/**
 * MCP 서버 활성화 상태에서 admin `admin_*` 도구 20개 (기존 13 + PR-05 Phase3 4 + PR-06 should-add 3)와
 * 사용자 `user_*` 도구 14개 (기존 9 + PR-05 verb 4 + briefing 1) — 총 34개가 모두 등록되는지 검증한다.
 */
@SpringBootTest(
    properties = [
        "clipping.mcp.server.enabled=true",
        "spring.ai.mcp.server.enabled=true",
        "clipping.mcp.service-token=test-mcp-service-token-for-integration-tests-32chars",
    ],
)
@ActiveProfiles("test")
class McpServerIntegrationTest {

    @Autowired
    lateinit var toolCallbackProvider: ToolCallbackProvider

    private val toolCallbacks by lazy { toolCallbackProvider.toolCallbacks.toList() }

    @Test
    fun `should register all 34 MCP tools (20 admin + 14 user)`() {
        toolCallbacks shouldHaveSize 34
    }

    @Test
    fun `should have expected tool names including new admin and user tools`() {
        val toolNames = toolCallbacks.map { it.toolDefinition.name() }.toSet()
        toolNames shouldBe setOf(
            // admin 도구 — 기존 10개
            "admin_list_categories",
            "admin_collect", "admin_summarize", "admin_daily_summary",
            "admin_collect_async", "admin_summarize_async", "admin_job_status",
            "admin_export", "admin_send_digest", "admin_pipeline",
            // admin 도구 — PR-04 모니터링 3개
            "admin_list_recent_jobs",
            "admin_list_failing_sources",
            "admin_list_pending_requests",
            // admin 도구 — PR-05 Phase 3 데이터 3개 + 재시도 1개
            "admin_content_levers_summary",
            "admin_list_organizations",
            "admin_category_organizations",
            "admin_retry_failed_delivery",
            // admin 도구 — PR-06 should-add 3개
            "admin_approve_pending_request",
            "admin_cost_summary",
            "admin_slack_channel_diagnose",
            // 사용자 도구 — 기존 9개
            "user_list_categories", "user_list_sources",
            "user_list_recent_summaries", "user_search_summaries", "user_list_top_summaries",
            "user_get_summary_detail", "user_get_original_preview",
            "user_get_category_overview", "user_get_trending_keywords",
            // 사용자 도구 — PR-05 verb 4개
            "user_toggle_feedback",
            "user_toggle_bookmark",
            "user_list_bookmarks",
            "user_list_my_subscriptions",
            // 사용자 도구 — briefing (구독 카테고리 통합 브리핑)
            "user_get_my_briefing",
        )
    }

    @Test
    fun `admin_list_categories should return categories array`() {
        val tool = toolCallbacks.first { it.toolDefinition.name() == "admin_list_categories" }
        val result = tool.call("{}")
        result shouldContain "categories"
    }
}
