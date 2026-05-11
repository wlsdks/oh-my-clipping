package com.ohmyclipping.mcp

import com.ohmyclipping.admin.mcp.AdminApprovePendingRequestTool
import com.ohmyclipping.admin.mcp.AdminCategoryListTool
import com.ohmyclipping.admin.mcp.AdminCollectAsyncTool
import com.ohmyclipping.admin.mcp.AdminCollectTool
import com.ohmyclipping.admin.mcp.AdminContentLeversSummaryTool
import com.ohmyclipping.admin.mcp.AdminCostSummaryTool
import com.ohmyclipping.admin.mcp.AdminDailySummaryTool
import com.ohmyclipping.admin.mcp.AdminExportTool
import com.ohmyclipping.admin.mcp.AdminJobStatusTool
import com.ohmyclipping.admin.mcp.AdminListFailingSourcesTool
import com.ohmyclipping.admin.mcp.AdminListPendingRequestsTool
import com.ohmyclipping.admin.mcp.AdminListRecentJobsTool
import com.ohmyclipping.admin.mcp.AdminOrganizationTools
import com.ohmyclipping.admin.mcp.AdminPipelineTool
import com.ohmyclipping.admin.mcp.AdminRetryFailedDeliveryTool
import com.ohmyclipping.admin.mcp.AdminSendDigestTool
import com.ohmyclipping.admin.mcp.AdminSlackChannelDiagnoseTool
import com.ohmyclipping.admin.mcp.AdminSummarizeAsyncTool
import com.ohmyclipping.admin.mcp.AdminSummarizeTool
import com.ohmyclipping.user.mcp.UserBookmarkTools
import com.ohmyclipping.user.mcp.UserCategoryTools
import com.ohmyclipping.user.mcp.UserFeedbackTools
import com.ohmyclipping.user.mcp.UserInsightTools
import com.ohmyclipping.user.mcp.UserSubscriptionTools
import com.ohmyclipping.user.mcp.UserSummaryDetailTools
import com.ohmyclipping.user.mcp.UserSummaryTools
import com.fasterxml.jackson.databind.ObjectMapper
import io.github.oshai.kotlinlogging.KotlinLogging
import io.modelcontextprotocol.server.McpServerFeatures
import org.springframework.ai.mcp.customizer.McpSyncServerCustomizer
import org.springframework.ai.tool.StaticToolCallbackProvider
import org.springframework.ai.tool.ToolCallbackProvider
import org.springframework.ai.tool.method.MethodToolCallbackProvider
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Primary

private val log = KotlinLogging.logger {}

/**
 * MCP 서버 활성화 시 도구 콜백 프로바이더를 빈으로 등록한다.
 *
 * PR-06 기준 admin `admin_*` 도구 20개(PR-05 17 + 신규 should-add 3)와
 * 사용자 `user_*` 도구 13개(기존 9 + 신규 verb 4)를 하나의 프로바이더로 합쳐 총 33개 도구를
 * [FilteredToolCallback]으로 감싸 반환한다.
 * 래퍼는 `_` prefix 파라미터를 `tools/list` 스키마에서 제거하여
 * LLM이 시스템 주입 컨텍스트 값을 임의로 채우지 못하게 한다.
 */
@Configuration
@ConditionalOnProperty(name = ["clipping.mcp.server.enabled"], havingValue = "true")
class McpServerConfig {

    @Bean
    @Primary
    @Suppress("LongParameterList")
    fun clippingToolCallbackProvider(
        // admin 도구 — 기존 10개 (PR-04 에서 admin/mcp/로 이전)
        adminCategoryListTool: AdminCategoryListTool,
        adminCollectTool: AdminCollectTool,
        adminSummarizeTool: AdminSummarizeTool,
        adminDailySummaryTool: AdminDailySummaryTool,
        adminCollectAsyncTool: AdminCollectAsyncTool,
        adminSummarizeAsyncTool: AdminSummarizeAsyncTool,
        adminJobStatusTool: AdminJobStatusTool,
        adminExportTool: AdminExportTool,
        adminSendDigestTool: AdminSendDigestTool,
        adminPipelineTool: AdminPipelineTool,
        // admin 도구 — 모니터링 3개 (PR-04)
        adminListRecentJobsTool: AdminListRecentJobsTool,
        adminListFailingSourcesTool: AdminListFailingSourcesTool,
        adminListPendingRequestsTool: AdminListPendingRequestsTool,
        // admin 도구 — Phase 3 신규 4개 (PR-05)
        adminContentLeversSummaryTool: AdminContentLeversSummaryTool,
        adminOrganizationTools: AdminOrganizationTools,
        adminRetryFailedDeliveryTool: AdminRetryFailedDeliveryTool,
        // admin 도구 — should-add 신규 3개 (PR-06)
        adminApprovePendingRequestTool: AdminApprovePendingRequestTool,
        adminCostSummaryTool: AdminCostSummaryTool,
        adminSlackChannelDiagnoseTool: AdminSlackChannelDiagnoseTool,
        // 사용자 도구 — PR-03 기존 9개
        userCategoryTools: UserCategoryTools,
        userSummaryTools: UserSummaryTools,
        userSummaryDetailTools: UserSummaryDetailTools,
        userInsightTools: UserInsightTools,
        // 사용자 도구 — PR-05 신규 verb 4개
        userFeedbackTools: UserFeedbackTools,
        userBookmarkTools: UserBookmarkTools,
        userSubscriptionTools: UserSubscriptionTools,
        objectMapper: ObjectMapper,
    ): ToolCallbackProvider {
        // 모든 도구 객체를 한 번에 MethodToolCallbackProvider로 등록한다.
        val methodProvider = MethodToolCallbackProvider.builder()
            .toolObjects(
                adminCategoryListTool,
                adminCollectTool, adminSummarizeTool, adminDailySummaryTool,
                adminCollectAsyncTool, adminSummarizeAsyncTool, adminJobStatusTool,
                adminExportTool, adminSendDigestTool, adminPipelineTool,
                adminListRecentJobsTool, adminListFailingSourcesTool, adminListPendingRequestsTool,
                adminContentLeversSummaryTool, adminOrganizationTools, adminRetryFailedDeliveryTool,
                adminApprovePendingRequestTool, adminCostSummaryTool, adminSlackChannelDiagnoseTool,
                userCategoryTools, userSummaryTools, userSummaryDetailTools, userInsightTools,
                userFeedbackTools, userBookmarkTools, userSubscriptionTools,
            )
            .build()

        // 각 콜백을 FilteredToolCallback으로 감싸 시스템 주입 파라미터를 숨긴다.
        val filteredCallbacks = methodProvider.toolCallbacks
            .map { FilteredToolCallback(it, objectMapper) }
            .toTypedArray()

        return StaticToolCallbackProvider(*filteredCallbacks)
    }

    /**
     * MCP `tools/list` 응답에 도구별 annotations(readOnlyHint / destructiveHint /
     * idempotentHint)를 주입한다.
     *
     * 스프링 AI 1.1.4 의 `McpToolUtils.toSharedSyncToolSpecification` 은
     * `ToolCallback.getToolDefinition()` 에서 name/description/inputSchema 만 읽어
     * `McpSchema.Tool` 을 빌드하므로 annotations 가 비어있다. 서버 기동 과정에서
     * Spring AI 가 이 customizer 를 호출할 때 spec 내부의 tools 리스트 각 원소를
     * 동일한 callHandler + (annotations 가 포함된) 새 `McpSchema.Tool` 로 교체한다.
     *
     * `McpServer.SyncSpecification.tools` 필드는 패키지-프라이빗 이므로 reflection
     * 으로 접근한다. 재귀호출/append API 인 `.tools(List)` 는 duplicate 체크가
     * 걸려 있어 재등록이 불가능하기 때문이다.
     *
     * [McpToolAnnotations.BY_NAME] 에 없는 도구 이름은 annotations 없이 유지된다.
     */
    @Bean
    fun annotationInjectingMcpSyncServerCustomizer(): McpSyncServerCustomizer =
        McpSyncServerCustomizer { spec ->
            val tools = resolveToolsList(spec) ?: return@McpSyncServerCustomizer
            var annotated = 0
            for (i in tools.indices) {
                val existing = tools[i]
                val annotations = McpToolAnnotations.BY_NAME[existing.tool().name()] ?: continue
                val originalTool = existing.tool()
                val annotatedTool = io.modelcontextprotocol.spec.McpSchema.Tool.builder()
                    .name(originalTool.name())
                    .description(originalTool.description())
                    .inputSchema(originalTool.inputSchema())
                    .annotations(annotations)
                    .build()
                tools[i] = McpServerFeatures.SyncToolSpecification.builder()
                    .tool(annotatedTool)
                    .callHandler(existing.callHandler())
                    .build()
                annotated++
            }
            log.info { "MCP tools annotated with readOnly/destructive/idempotent hints: $annotated/${tools.size}" }
        }

    /**
     * `McpServer.SyncSpecification.tools` (package-private ArrayList) 를 reflection
     * 으로 꺼낸다. 접근 실패 시 경고만 남기고 null 을 돌려준다 — 서버 기동을 막지
     * 않는다.
     */
    @Suppress("UNCHECKED_CAST", "SwallowedException")
    private fun resolveToolsList(
        spec: io.modelcontextprotocol.server.McpServer.SyncSpecification<*>,
    ): MutableList<McpServerFeatures.SyncToolSpecification>? = try {
        val field = io.modelcontextprotocol.server.McpServer.SyncSpecification::class.java
            .getDeclaredField("tools")
        field.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        field.get(spec) as? MutableList<McpServerFeatures.SyncToolSpecification>
    } catch (e: ReflectiveOperationException) {
        log.warn { "MCP tools 필드 접근 실패 — annotations 주입을 건너뜀: ${e.message}" }
        null
    }
}
