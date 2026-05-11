package com.ohmyclipping.model

import java.time.Instant

data class RuntimeSetting(
    val key: String,
    val value: String,
    val updatedAt: Instant = Instant.now()
)
