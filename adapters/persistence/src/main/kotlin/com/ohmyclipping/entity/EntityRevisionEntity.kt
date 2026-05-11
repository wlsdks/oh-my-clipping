package com.ohmyclipping.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Index
import jakarta.persistence.Table
import java.time.Instant

/**
 * 엔티티 변경 이력 엔티티.
 *
 * `entity_revision_history` 테이블에 매핑되며, Persona/Category/CategoryRule/RssSource 수정 이력을
 * 하나의 append-only 테이블에 저장한다. 도메인별 V-scheme (persona_versions 등)은 유지하되,
 * 통합 히스토리 API(`/history`, `/restore`)는 이 테이블만 본다.
 */
@Entity
@Table(
    name = "entity_revision_history",
    indexes = [
        Index(name = "idx_entity_revision_resource", columnList = "resource_type, resource_id, revision_number"),
        Index(name = "idx_entity_revision_created", columnList = "created_at")
    ]
)
class EntityRevisionEntity(
    @Id
    @Column(length = 36)
    val id: String = "",

    @Column(name = "resource_type", length = 32, nullable = false)
    val resourceType: String = "",

    @Column(name = "resource_id", length = 36, nullable = false)
    val resourceId: String = "",

    @Column(name = "revision_number", nullable = false)
    val revisionNumber: Long = 0L,

    @Column(name = "editor_id", length = 100, nullable = false)
    val editorId: String = "",

    @Column(name = "editor_display_name", length = 200)
    val editorDisplayName: String? = null,

    @Column(name = "changed_fields", columnDefinition = "TEXT")
    val changedFields: String? = null,

    @Column(name = "snapshot", columnDefinition = "TEXT", nullable = false)
    val snapshot: String = "",

    @Column(name = "created_at", nullable = false)
    val createdAt: Instant = Instant.now()
)
