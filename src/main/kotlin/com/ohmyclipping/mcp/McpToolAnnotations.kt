package com.ohmyclipping.mcp

import io.modelcontextprotocol.spec.McpSchema

/**
 * 각 MCP 도구의 행위 힌트 매핑.
 *
 * MCP 스펙의 `annotations` 필드는 클라이언트(LLM/orchestrator)에게 해당 도구가
 * 읽기 전용인지, 되돌릴 수 없는 파괴적 액션인지, 같은 입력으로 여러 번 호출해도
 * 안전한지(멱등) 등을 알려 준다.
 *
 * 분류 기준:
 *  - **readOnlyHint=true**: DB/외부 상태를 **수정하지 않음**. `list_*`, `get_*`,
 *    `search_*`, `export`(조회만) 류.
 *  - **destructiveHint=true + idempotentHint=false**: **외부에 되돌릴 수 없는 부작용**
 *    (예: Slack 메시지 게시). 사용자 확인 없이 자동 호출되면 안 된다.
 *  - **idempotentHint=false**: 같은 입력으로 여러 번 호출하면 결과가 달라질 수 있는
 *    작업(수집/요약/파이프라인 등 새로운 레코드를 생성/누적).
 *
 * 스프링 AI 1.1.4 의 `McpToolUtils.toSharedSyncToolSpecification` 은
 * `ToolCallback.getToolDefinition()` 에서 name/description/inputSchema 만 읽어
 * `McpSchema.Tool` 을 빌드하며 annotations 를 채우지 않는다. 따라서 서버 기동 시
 * [AnnotationInjectingMcpSyncServerCustomizer] 가 이 맵을 바탕으로 기존 Tool 를
 * 새로운 (annotations 포함) Tool 로 교체한다.
 */
object McpToolAnnotations {

    /**
     * tool name → ToolAnnotations.
     * 맵에 없는 도구는 annotations 없이 노출된다.
     */
    val BY_NAME: Map<String, McpSchema.ToolAnnotations> = buildMap {
        // --- read-only (safe to call autonomously) ---
        putAll(readOnly("admin_list_categories"))
        putAll(readOnly("admin_list_recent_jobs"))
        putAll(readOnly("admin_list_failing_sources"))
        putAll(readOnly("admin_list_pending_requests"))
        putAll(readOnly("admin_job_status"))
        // admin_export reads records only (no mutation) — read-only from the DB's POV.
        putAll(readOnly("admin_export"))
        putAll(readOnly("user_list_categories"))
        putAll(readOnly("user_list_sources"))
        putAll(readOnly("user_list_recent_summaries"))
        putAll(readOnly("user_list_top_summaries"))
        putAll(readOnly("user_search_summaries"))
        putAll(readOnly("user_get_summary_detail"))
        putAll(readOnly("user_get_original_preview"))
        putAll(readOnly("user_get_trending_keywords"))
        putAll(readOnly("user_get_category_overview"))
        // PR-05 신규 read-only 사용자 도구
        putAll(readOnly("user_list_bookmarks"))
        putAll(readOnly("user_list_my_subscriptions"))
        // PR-05 신규 read-only admin 도구 (Phase 3 데이터)
        putAll(readOnly("admin_content_levers_summary"))
        putAll(readOnly("admin_list_organizations"))
        putAll(readOnly("admin_category_organizations"))
        // PR-06 신규 read-only admin 도구 (should-add)
        putAll(readOnly("admin_cost_summary"))
        putAll(readOnly("admin_slack_channel_diagnose"))

        // --- idempotent upsert (user feedback — 같은 (userId, summaryId) 는 1 row) ---
        put(
            "user_toggle_feedback",
            toolAnnotations(readOnlyHint = false, destructiveHint = false, idempotentHint = true),
        )

        // --- destructive external side-effect (Slack posting) ---
        put(
            "admin_send_digest",
            toolAnnotations(readOnlyHint = false, destructiveHint = true, idempotentHint = false),
        )
        // PR-05: 실패 발송 재시도 — Slack 재게시가 일어남 (destructive + non-idempotent).
        put(
            "admin_retry_failed_delivery",
            toolAnnotations(readOnlyHint = false, destructiveHint = true, idempotentHint = false),
        )
        // PR-06: 대기 요청 승인 — Category/Persona/Source 생성 + 사용자 DM. 되돌릴 수 없음.
        put(
            "admin_approve_pending_request",
            toolAnnotations(readOnlyHint = false, destructiveHint = true, idempotentHint = false),
        )

        // --- non-idempotent writes (collect/summarize/pipeline) ---
        putAll(nonIdempotent("admin_collect"))
        putAll(nonIdempotent("admin_summarize"))
        putAll(nonIdempotent("admin_daily_summary"))
        putAll(nonIdempotent("admin_pipeline"))
        putAll(nonIdempotent("admin_collect_async"))
        putAll(nonIdempotent("admin_summarize_async"))
        // PR-05: 북마크 토글은 상태가 뒤집히므로 non-idempotent.
        putAll(nonIdempotent("user_toggle_bookmark"))
    }

    private fun readOnly(name: String): Map<String, McpSchema.ToolAnnotations> = mapOf(
        name to toolAnnotations(
            readOnlyHint = true,
            destructiveHint = false,
            idempotentHint = true,
        ),
    )

    private fun nonIdempotent(name: String): Map<String, McpSchema.ToolAnnotations> = mapOf(
        name to toolAnnotations(
            readOnlyHint = false,
            destructiveHint = false,
            idempotentHint = false,
        ),
    )

    /**
     * [McpSchema.ToolAnnotations] 팩토리. title/openWorldHint/returnDirect 는 null 로 둔다.
     */
    internal fun toolAnnotations(
        readOnlyHint: Boolean,
        destructiveHint: Boolean,
        idempotentHint: Boolean,
    ): McpSchema.ToolAnnotations = McpSchema.ToolAnnotations(
        /* title = */ null,
        /* readOnlyHint = */ readOnlyHint,
        /* destructiveHint = */ destructiveHint,
        /* idempotentHint = */ idempotentHint,
        /* openWorldHint = */ null,
        /* returnDirect = */ null,
    )
}
