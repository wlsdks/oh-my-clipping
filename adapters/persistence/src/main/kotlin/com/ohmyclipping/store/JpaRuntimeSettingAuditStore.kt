package com.ohmyclipping.store

import com.ohmyclipping.entity.RuntimeSettingAuditEntity
import com.ohmyclipping.model.RuntimeSettingAudit
import com.ohmyclipping.repository.RuntimeSettingAuditRepository
import org.springframework.context.annotation.Primary
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Repository

/**
 * 런타임 설정 감사 이력 JPA 구현.
 */
@Repository
@Primary
class JpaRuntimeSettingAuditStore(
    private val repository: RuntimeSettingAuditRepository
) : RuntimeSettingAuditStore {

    override fun list(limit: Int): List<RuntimeSettingAudit> =
        repository.findAllByOrderByChangedAtDesc(PageRequest.of(0, limit))
            .map { it.toModel() }

    override fun saveAll(audits: List<RuntimeSettingAudit>): List<RuntimeSettingAudit> =
        repository.saveAll(audits.map { it.toEntity() }).map { it.toModel() }

    private fun RuntimeSettingAuditEntity.toModel() = RuntimeSettingAudit(
        id = id,
        settingKey = settingKey,
        oldValue = oldValue,
        newValue = newValue,
        action = action,
        changedBy = changedBy,
        changedAt = changedAt
    )

    private fun RuntimeSettingAudit.toEntity() = RuntimeSettingAuditEntity(
        id = id,
        settingKey = settingKey,
        oldValue = oldValue,
        newValue = newValue,
        action = action,
        changedBy = changedBy,
        changedAt = changedAt
    )
}
