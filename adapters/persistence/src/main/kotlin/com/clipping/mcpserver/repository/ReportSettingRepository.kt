package com.clipping.mcpserver.repository

import com.clipping.mcpserver.entity.ReportSettingEntity
import org.springframework.data.jpa.repository.JpaRepository

interface ReportSettingRepository : JpaRepository<ReportSettingEntity, String> {
    fun findBySettingKey(settingKey: String): ReportSettingEntity?
}
