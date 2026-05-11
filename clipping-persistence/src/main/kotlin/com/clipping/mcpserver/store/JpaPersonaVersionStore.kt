package com.clipping.mcpserver.store

import com.clipping.mcpserver.entity.PersonaVersionEntity
import com.clipping.mcpserver.repository.PersonaVersionRepository
import com.clipping.mcpserver.model.PersonaVersionDetail
import com.clipping.mcpserver.model.PersonaVersionSummary
import org.springframework.context.annotation.Primary
import org.springframework.stereotype.Repository
import java.util.UUID

/**
 * 페르소나 버전 스냅샷 JPA 구현. JdbcPersonaVersionStore를 대체한다.
 */
@Repository
@Primary
class JpaPersonaVersionStore(
    private val repository: PersonaVersionRepository
) : PersonaVersionStore {

    override fun listByPersonaId(personaId: String): List<PersonaVersionSummary> =
        repository.findByPersonaIdOrderByVersionDesc(personaId).map { it.toSummary() }

    override fun findByPersonaIdAndVersion(personaId: String, version: Int): PersonaVersionDetail? =
        repository.findByPersonaIdAndVersion(personaId, version)?.toDetail()

    override fun saveSnapshot(
        personaId: String,
        version: Int,
        detail: PersonaVersionDetail,
        changeSummary: String
    ) {
        val id = UUID.randomUUID().toString()
        val entity = PersonaVersionEntity(
            id = id,
            personaId = personaId,
            version = version,
            name = detail.name,
            description = detail.description,
            systemPrompt = detail.systemPrompt,
            summaryStyle = detail.summaryStyle,
            targetAudience = detail.targetAudience,
            maxItems = detail.maxItems,
            language = detail.language,
            previewTitle = detail.previewTitle,
            previewSource = detail.previewSource,
            previewBody = detail.previewBody,
            changeSummary = changeSummary
        )
        repository.save(entity)
    }

    // ── private helpers ──

    private fun PersonaVersionEntity.toSummary() = PersonaVersionSummary(
        version = version,
        changeSummary = changeSummary ?: "",
        createdAt = createdAt
    )

    private fun PersonaVersionEntity.toDetail() = PersonaVersionDetail(
        version = version,
        name = name,
        description = description,
        systemPrompt = systemPrompt,
        summaryStyle = summaryStyle,
        targetAudience = targetAudience,
        maxItems = maxItems,
        language = language,
        previewTitle = previewTitle,
        previewSource = previewSource,
        previewBody = previewBody,
        changeSummary = changeSummary,
        createdAt = createdAt
    )
}
