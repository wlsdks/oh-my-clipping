package com.clipping.mcpserver.model

import java.time.Instant

data class RuntimeSetting(
    val key: String,
    val value: String,
    val updatedAt: Instant = Instant.now()
)
