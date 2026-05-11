package com.ohmyclipping.entity

import jakarta.persistence.*
import java.time.Instant

/**
 * 클리핑 페르소나 엔티티.
 * clipping_personas 테이블에 매핑된다.
 */
@Entity
@Table(name = "clipping_personas")
class PersonaEntity(
    @Id
    @Column(length = 36)
    val id: String = "",

    @Column(length = 200, nullable = false)
    var name: String = "",

    @Column(columnDefinition = "TEXT")
    var description: String? = null,

    @Column(name = "system_prompt", columnDefinition = "TEXT", nullable = false)
    var systemPrompt: String = "",

    @Column(name = "summary_style", columnDefinition = "TEXT")
    var summaryStyle: String? = null,

    @Column(name = "target_audience", columnDefinition = "TEXT")
    var targetAudience: String? = null,

    @Column(name = "max_items", nullable = false)
    var maxItems: Int = 5,

    @Column(length = 10, nullable = false)
    var language: String = "ko",

    @Column(name = "is_active", nullable = false)
    var isActive: Boolean = true,

    @Column(name = "is_preset", nullable = false)
    var isPreset: Boolean = false,

    @Column(name = "preview_title", columnDefinition = "TEXT")
    var previewTitle: String? = null,

    @Column(name = "preview_source", columnDefinition = "TEXT")
    var previewSource: String? = null,

    @Column(name = "preview_body", columnDefinition = "TEXT")
    var previewBody: String? = null,

    @Column(name = "current_version", nullable = false)
    var currentVersion: Int = 1,

    @Column(length = 50)
    var tone: String? = null,

    @Column(name = "length_pref", length = 50)
    var lengthPref: String? = null,

    @Column(name = "created_at", nullable = false)
    val createdAt: Instant = Instant.now(),

    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant = Instant.now(),

    /**
     * 스케줄러/배치가 마지막으로 건드린 시각. 사용자 편집 시각과 분리한다.
     */
    @Column(name = "system_updated_at", nullable = false)
    var systemUpdatedAt: Instant = Instant.now()
)
