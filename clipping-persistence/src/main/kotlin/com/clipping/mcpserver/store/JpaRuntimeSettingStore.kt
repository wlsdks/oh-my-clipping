package com.clipping.mcpserver.store

import com.clipping.mcpserver.entity.RuntimeSettingEntity
import com.clipping.mcpserver.model.RuntimeSetting
import com.clipping.mcpserver.repository.RuntimeSettingRepository
import org.springframework.context.annotation.Primary
import org.springframework.stereotype.Repository
import java.time.Instant

/**
 * 런타임 설정 JPA 구현. JdbcRuntimeSettingStore를 대체한다.
 */
@Repository
@Primary
class JpaRuntimeSettingStore(
    private val repository: RuntimeSettingRepository
) : RuntimeSettingStore {

    override fun list(): List<RuntimeSetting> =
        repository.findAll().map { it.toModel() }.sortedBy { it.key }

    override fun findByKey(key: String): RuntimeSetting? =
        repository.findById(key).orElse(null)?.toModel()

    override fun save(setting: RuntimeSetting): RuntimeSetting {
        val now = Instant.now()
        val entity = repository.findById(setting.key).orElse(null)
        if (entity != null) {
            entity.settingValue = setting.value
            entity.updatedAt = now
            return repository.save(entity).toModel()
        }
        return repository.save(
            RuntimeSettingEntity(
                settingKey = setting.key,
                settingValue = setting.value,
                updatedAt = now
            )
        ).toModel()
    }

    override fun saveAll(settings: List<RuntimeSetting>): List<RuntimeSetting> =
        settings.map { save(it) }

    override fun deleteAll(): Int {
        val count = repository.count().toInt()
        repository.deleteAll()
        return count
    }

    private fun RuntimeSettingEntity.toModel() = RuntimeSetting(
        key = settingKey,
        value = settingValue,
        updatedAt = updatedAt
    )
}
