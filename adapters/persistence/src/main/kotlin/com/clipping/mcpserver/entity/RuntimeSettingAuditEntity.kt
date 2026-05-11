package com.clipping.mcpserver.entity

import jakarta.persistence.*
import java.time.Instant

/**
 * 런타임 설정 변경 감사 이력 엔티티.
 * clipping_runtime_settings_audits 테이블에 매핑된다.
 */
@Entity
@Table(name = "clipping_runtime_settings_audits")
class RuntimeSettingAuditEntity(
    @Id
    @Column(length = 36)
    val id: String = "",

    @Column(name = "setting_key", length = 120, nullable = false)
    val settingKey: String = "",

    @Column(name = "old_value", columnDefinition = "TEXT")
    val oldValue: String? = null,

    @Column(name = "new_value", columnDefinition = "TEXT")
    val newValue: String? = null,

    @Column(length = 20, nullable = false)
    val action: String = "UPDATE",

    @Column(name = "changed_by", length = 100, nullable = false)
    val changedBy: String = "system",

    @Column(name = "changed_at", nullable = false)
    val changedAt: Instant = Instant.now()
)
