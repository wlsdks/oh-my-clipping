package com.clipping.mcpserver.repository

import com.clipping.mcpserver.entity.RuntimeSettingAuditEntity
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository

interface RuntimeSettingAuditRepository : JpaRepository<RuntimeSettingAuditEntity, String> {
    fun findAllByOrderByChangedAtDesc(pageable: Pageable): List<RuntimeSettingAuditEntity>
}
