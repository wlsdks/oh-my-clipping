package com.clipping.mcpserver.entity

import jakarta.persistence.*
import java.time.Instant

/**
 * 리포트 설정 key-value 엔티티.
 * report_settings 테이블에 매핑된다.
 */
@Entity
@Table(name = "report_settings")
class ReportSettingEntity(
    @Id
    @Column(length = 36)
    val id: String = "",

    @Column(name = "setting_key", length = 50, nullable = false, unique = true)
    val settingKey: String = "",

    @Column(name = "setting_value", columnDefinition = "TEXT", nullable = false)
    var settingValue: String = "",

    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant = Instant.now()
)
