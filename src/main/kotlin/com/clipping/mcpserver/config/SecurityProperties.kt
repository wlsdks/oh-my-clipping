package com.clipping.mcpserver.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "clipping-mcp-server.security")
data class SecurityProperties(
    val adminToken: String = "",
    val allowSignup: Boolean = false,
    val allowUserSignup: Boolean = true,
    val allowBootstrapSignup: Boolean = false,
    val minPasswordLength: Int = 8
)
