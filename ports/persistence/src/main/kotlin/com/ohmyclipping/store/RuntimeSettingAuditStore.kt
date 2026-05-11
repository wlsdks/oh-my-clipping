package com.ohmyclipping.store

import com.ohmyclipping.model.RuntimeSettingAudit

interface RuntimeSettingAuditStore {
    fun list(limit: Int = 30): List<RuntimeSettingAudit>
    fun saveAll(audits: List<RuntimeSettingAudit>): List<RuntimeSettingAudit>
}
