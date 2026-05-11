package com.clipping.mcpserver.store

import com.clipping.mcpserver.model.RuntimeSettingAudit

interface RuntimeSettingAuditStore {
    fun list(limit: Int = 30): List<RuntimeSettingAudit>
    fun saveAll(audits: List<RuntimeSettingAudit>): List<RuntimeSettingAudit>
}
