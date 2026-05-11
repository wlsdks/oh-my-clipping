package com.clipping.mcpserver.entity

import jakarta.persistence.*
import java.time.Instant

/**
 * 런타임 설정 key-value 엔티티.
 * clipping_runtime_settings 테이블에 매핑된다.
 */
@Entity
@Table(name = "clipping_runtime_settings")
class RuntimeSettingEntity(
    @Id
    @Column(name = "setting_key", length = 120)
    val settingKey: String = "",

    @Column(name = "setting_value", columnDefinition = "TEXT", nullable = false)
    var settingValue: String = "",

    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant = Instant.now()
)
