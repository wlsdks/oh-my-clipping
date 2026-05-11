package com.clipping.mcpserver.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "clipping-mcp-server.slack")
data class SlackProperties(
    val botToken: String = "",
    val appLevelToken: String = "",
    val socketModeEnabled: Boolean = false,
    val dailyChannelMessageLimit: Int = 100,
    val apiBaseUrl: String = "https://slack.com/api",
    val connectTimeoutMs: Int = 10000,
    val readTimeoutMs: Int = 15000,
    val autoDigestEnabled: Boolean = false,
    val digestCron: String = "-",
    val autoDigestMaxItems: Int = 5,
    val autoDigestUnsentOnly: Boolean = true
)
