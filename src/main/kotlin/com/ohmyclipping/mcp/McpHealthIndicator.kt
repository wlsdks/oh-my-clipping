package com.ohmyclipping.mcp

import org.springframework.ai.tool.ToolCallbackProvider
import org.springframework.boot.actuate.health.Health
import org.springframework.boot.actuate.health.HealthIndicator
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Component

/** MCP 서버 헬스 인디케이터 — 등록된 도구 개수를 함께 리포트한다. */
@Component
@ConditionalOnProperty(name = ["clipping.mcp.server.enabled"], havingValue = "true")
class McpHealthIndicator(
    private val toolCallbackProvider: ToolCallbackProvider,
) : HealthIndicator {
    override fun health(): Health =
        Health.up()
            .withDetail("mcp.tools.registered", toolCallbackProvider.toolCallbacks.size)
            .build()
}
