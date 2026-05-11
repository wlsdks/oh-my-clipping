package com.clipping.mcpserver.store

import com.clipping.mcpserver.entity.ReportSettingEntity
import com.clipping.mcpserver.repository.ReportSettingRepository
import org.springframework.context.annotation.Primary
import org.springframework.stereotype.Repository
import java.time.Instant
import java.util.UUID

/**
 * 리포트 설정 JPA 구현. JdbcReportSettingsStore를 대체한다.
 */
@Repository
@Primary
class JpaReportSettingsStore(
    private val repository: ReportSettingRepository
) : ReportSettingsStore {

    override fun findAll(): Map<String, String> =
        repository.findAll().associate { it.settingKey to it.settingValue }

    override fun findByKey(key: String): String? =
        repository.findBySettingKey(key)?.settingValue

    override fun upsert(key: String, value: String) {
        val existing = repository.findBySettingKey(key)
        if (existing != null) {
            existing.settingValue = value
            existing.updatedAt = Instant.now()
            repository.save(existing)
        } else {
            repository.save(
                ReportSettingEntity(
                    id = UUID.randomUUID().toString(),
                    settingKey = key,
                    settingValue = value,
                    updatedAt = Instant.now()
                )
            )
        }
    }
}
