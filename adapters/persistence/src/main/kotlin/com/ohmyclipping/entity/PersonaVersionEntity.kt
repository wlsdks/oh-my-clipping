package com.ohmyclipping.entity

import jakarta.persistence.*
import java.time.Instant

/**
 * 페르소나 버전 스냅샷 엔티티.
 * persona_versions 테이블에 매핑된다.
 */
@Entity
@Table(
    name = "persona_versions",
    uniqueConstraints = [UniqueConstraint(columnNames = ["persona_id", "version"])]
)
class PersonaVersionEntity(
    @Id
    @Column(length = 36)
    val id: String = "",

    @Column(name = "persona_id", length = 36, nullable = false)
    val personaId: String = "",

    @Column(nullable = false)
    val version: Int = 1,

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

    @Column(name = "preview_title", columnDefinition = "TEXT")
    var previewTitle: String? = null,

    @Column(name = "preview_source", columnDefinition = "TEXT")
    var previewSource: String? = null,

    @Column(name = "preview_body", columnDefinition = "TEXT")
    var previewBody: String? = null,

    @Column(name = "change_summary", columnDefinition = "TEXT")
    var changeSummary: String? = null,

    @Column(name = "created_at", nullable = false)
    val createdAt: Instant = Instant.now()
)
