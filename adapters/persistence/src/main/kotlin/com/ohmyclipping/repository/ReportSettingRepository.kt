package com.ohmyclipping.repository

import com.ohmyclipping.entity.ReportSettingEntity
import org.springframework.data.jpa.repository.JpaRepository

interface ReportSettingRepository : JpaRepository<ReportSettingEntity, String> {
    fun findBySettingKey(settingKey: String): ReportSettingEntity?
}
