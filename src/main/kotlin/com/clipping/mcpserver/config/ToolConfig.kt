package com.clipping.mcpserver.config

import com.clipping.mcpserver.admin.mcp.AdminCategoryListTool
import com.clipping.mcpserver.admin.mcp.AdminCollectAsyncTool
import com.clipping.mcpserver.admin.mcp.AdminCollectTool
import com.clipping.mcpserver.admin.mcp.AdminDailySummaryTool
import com.clipping.mcpserver.admin.mcp.AdminExportTool
import com.clipping.mcpserver.admin.mcp.AdminJobStatusTool
import com.clipping.mcpserver.admin.mcp.AdminListFailingSourcesTool
import com.clipping.mcpserver.admin.mcp.AdminListPendingRequestsTool
import com.clipping.mcpserver.admin.mcp.AdminListRecentJobsTool
import com.clipping.mcpserver.admin.mcp.AdminPipelineTool
import com.clipping.mcpserver.admin.mcp.AdminSendDigestTool
import com.clipping.mcpserver.admin.mcp.AdminSummarizeAsyncTool
import com.clipping.mcpserver.admin.mcp.AdminSummarizeTool
import org.springframework.ai.tool.ToolCallbackProvider
import org.springframework.ai.tool.method.MethodToolCallbackProvider
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/**
 * MCP 서버가 비활성화된 런타임(기본)에서 도구 콜백을 제공하는 폴백 설정.
 *
 * MCP 서버가 활성화되면 [com.clipping.mcpserver.mcp.McpServerConfig]가
 * `@Primary`로 덮어쓴다. 이 설정은 기존 admin 도구만 노출하며,
 * 사용자용 `user_*` 도구나 MCP 응답 래퍼는 포함하지 않는다.
 */
@Configuration
@ConditionalOnProperty(name = ["clipping.mcp.server.enabled"], havingValue = "false", matchIfMissing = true)
class ToolConfig {

    @Bean
    fun toolCallbackProvider(
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
        adminListRecentJobsTool: AdminListRecentJobsTool,
        adminListFailingSourcesTool: AdminListFailingSourcesTool,
        adminListPendingRequestsTool: AdminListPendingRequestsTool,
    ): ToolCallbackProvider = MethodToolCallbackProvider.builder()
        .toolObjects(
            adminCategoryListTool,
            adminCollectTool, adminSummarizeTool, adminDailySummaryTool,
            adminCollectAsyncTool, adminSummarizeAsyncTool, adminJobStatusTool,
            adminExportTool, adminSendDigestTool, adminPipelineTool,
            adminListRecentJobsTool, adminListFailingSourcesTool, adminListPendingRequestsTool,
        )
        .build()
}
