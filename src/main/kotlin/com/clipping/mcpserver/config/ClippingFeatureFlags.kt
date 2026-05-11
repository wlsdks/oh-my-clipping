package com.clipping.mcpserver.config

import org.springframework.boot.context.properties.ConfigurationProperties

/** 기능 토글 — scheduler, slack delivery, MCP server 활성화 제어. */
@ConfigurationProperties(prefix = "clipping")
data class ClippingFeatureFlags(
    val scheduler: SchedulerFlags = SchedulerFlags(),
    val slack: SlackFlags = SlackFlags(),
    val mcp: McpFlags = McpFlags(),
) {
    data class SchedulerFlags(val enabled: Boolean = true)
    data class SlackFlags(val delivery: DeliveryFlags = DeliveryFlags()) {
        data class DeliveryFlags(val enabled: Boolean = true)
    }
    data class McpFlags(val server: ServerFlags = ServerFlags()) {
        data class ServerFlags(val enabled: Boolean = false)
    }
}
