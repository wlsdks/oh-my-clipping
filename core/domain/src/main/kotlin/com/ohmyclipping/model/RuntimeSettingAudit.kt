package com.ohmyclipping.model

import java.time.Instant

data class RuntimeSettingAudit(
    val id: String,
    val settingKey: String,
    val oldValue: String? = null,
    val newValue: String? = null,
    val action: String,
    val changedBy: String,
    val changedAt: Instant = Instant.now()
)
