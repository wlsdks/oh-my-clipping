package com.clipping.mcpserver.store

import com.clipping.mcpserver.entity.PersonaEntity
import com.clipping.mcpserver.error.NotFoundException
import com.clipping.mcpserver.model.Persona
import com.clipping.mcpserver.repository.PersonaRepository
import jakarta.persistence.EntityManager
import org.springframework.context.annotation.Primary
import org.springframework.stereotype.Repository
import java.time.Instant
import java.util.UUID

/**
 * 페르소나 JPA 구현. JdbcPersonaStore를 대체한다.
 * 단순 CRUD는 JPA Repository, 복잡한 JOIN 집계는 EntityManager 네이티브 쿼리를 사용한다.
 */
@Repository
@Primary
class JpaPersonaStore(
    private val repository: PersonaRepository,
    private val em: EntityManager
) : PersonaStore {

    override fun list(): List<Persona> =
        repository.findAll().map { it.toModel() }.sortedBy { it.createdAt }

    override fun listActive(): List<Persona> =
        repository.findByIsActiveTrue().map { it.toModel() }.sortedBy { it.createdAt }

    override fun listPresets(): List<Persona> =
        repository.findByIsPresetTrue()
            .filter { it.isActive }
            .map { it.toModel() }
            .sortedBy { it.createdAt }

    override fun findById(id: String): Persona? =
        repository.findById(id).orElse(null)?.toModel()

    override fun save(persona: Persona): Persona {
        val now = Instant.now()
        val id = persona.id.ifBlank { UUID.randomUUID().toString() }
        val entity = PersonaEntity(
            id = id,
            name = persona.name,
            description = persona.description,
            systemPrompt = persona.systemPrompt,
            summaryStyle = persona.summaryStyle,
            targetAudience = persona.targetAudience,
            maxItems = persona.maxItems,
            language = persona.language,
            isActive = persona.isActive,
            isPreset = persona.isPreset,
            previewTitle = persona.previewTitle,
            previewSource = persona.previewSource,
            previewBody = persona.previewBody,
            currentVersion = persona.currentVersion,
            tone = persona.tone,
            lengthPref = persona.lengthPref,
            createdAt = now,
            updatedAt = now,
            systemUpdatedAt = now
        )
        return repository.save(entity).toModel()
    }

    override fun update(persona: Persona): Persona {
        val entity = repository.findById(persona.id).orElseThrow {
            NotFoundException("Persona not found: ${persona.id}")
        }
        val now = Instant.now()
        applyPersonaToEntity(persona, entity)
        entity.updatedAt = now
        entity.systemUpdatedAt = now
        return repository.save(entity).toModel()
    }

    override fun updateWithExpectedUpdatedAt(persona: Persona, expectedUpdatedAt: Instant): Persona? {
        val entity = repository.findById(persona.id).orElse(null) ?: return null
        // 낙관적 잠금: 기대 시각과 엔티티 updated_at이 일치하지 않으면 충돌로 처리한다.
        if (entity.updatedAt != expectedUpdatedAt) return null
        val now = Instant.now()
        applyPersonaToEntity(persona, entity)
        entity.updatedAt = now
        entity.systemUpdatedAt = now
        return repository.save(entity).toModel()
    }

    /** Persona 도메인 객체의 필드를 엔티티로 복사한다. 시간은 호출자가 별도 갱신. */
    private fun applyPersonaToEntity(persona: Persona, entity: PersonaEntity) {
        entity.name = persona.name
        entity.description = persona.description
        entity.systemPrompt = persona.systemPrompt
        entity.summaryStyle = persona.summaryStyle
        entity.targetAudience = persona.targetAudience
        entity.maxItems = persona.maxItems
        entity.language = persona.language
        entity.isActive = persona.isActive
        entity.isPreset = persona.isPreset
        entity.previewTitle = persona.previewTitle
        entity.previewSource = persona.previewSource
        entity.previewBody = persona.previewBody
        entity.currentVersion = persona.currentVersion
        entity.tone = persona.tone
        entity.lengthPref = persona.lengthPref
    }

    override fun delete(id: String) {
        repository.deleteById(id)
    }

    override fun countCustomPersonas(): Long =
        repository.countByIsPresetFalse()

    /** 프리셋별 활성 구독 수를 LEFT JOIN으로 집계한다. */
    @Suppress("UNCHECKED_CAST")
    override fun findPresetUsage(): List<PresetUsageRow> {
        val sql = """
            SELECT p.id, p.name, COUNT(c.id) AS cnt
            FROM clipping_personas p
            LEFT JOIN batch_categories c ON c.persona_id = p.id AND c.is_active = TRUE
            WHERE p.is_preset = TRUE
            GROUP BY p.id, p.name
            ORDER BY cnt DESC
        """.trimIndent()
        val rows = em.createNativeQuery(sql).resultList as List<Array<Any?>>
        return rows.mapNotNull { row ->
            val presetId = row[0] as? String ?: return@mapNotNull null
            val presetName = row[1] as? String ?: return@mapNotNull null
            val activeSubscriptions = (row[2] as? Number)?.toLong() ?: return@mapNotNull null
            PresetUsageRow(
                presetId = presetId,
                presetName = presetName,
                activeSubscriptions = activeSubscriptions
            )
        }
    }

    /** 커스텀 페르소나를 사용하는 활성 구독 수를 반환한다. */
    override fun countActiveCustomSubscriptions(): Long {
        val sql = """
            SELECT COUNT(*) FROM batch_categories c
            JOIN clipping_personas p ON c.persona_id = p.id
            WHERE p.is_preset = FALSE AND c.is_active = TRUE
        """.trimIndent()
        return (em.createNativeQuery(sql).singleResult as Number).toLong()
    }

    /** 특정 페르소나를 사용하는 활성 구독 수를 반환한다. */
    override fun countActiveSubscriptions(personaId: String): Long {
        val sql = "SELECT COUNT(*) FROM batch_categories WHERE persona_id = ? AND is_active = TRUE"
        val query = em.createNativeQuery(sql)
        query.setParameter(1, personaId)
        return (query.singleResult as Number).toLong()
    }

    /** 최근 생성된 커스텀 페르소나를 최대 limit 건 반환한다. */
    @Suppress("UNCHECKED_CAST")
    override fun findRecentCustomPersonas(limit: Int): List<RecentCustomPersonaRow> {
        val sql = """
            SELECT p.id, p.name AS persona_name, p.system_prompt,
                   p.tone, p.length_pref, p.created_at,
                   COALESCE(u.display_name, u.username, '알 수 없음') AS user_name
            FROM clipping_personas p
            LEFT JOIN clipping_user_owned_personas o ON o.persona_id = p.id
            LEFT JOIN admin_users u ON u.id = o.user_id
            WHERE p.is_preset = FALSE
            ORDER BY p.created_at DESC
            LIMIT ?
        """.trimIndent()
        val query = em.createNativeQuery(sql)
        query.setParameter(1, limit)
        val rows = query.resultList as List<Array<Any?>>
        return rows.mapNotNull { row ->
            val id = row[0] as? String ?: return@mapNotNull null
            val personaName = row[1] as? String ?: return@mapNotNull null
            val createdAt = coercePersonaCreatedAt(row[5]) ?: return@mapNotNull null
            RecentCustomPersonaRow(
                id = id,
                personaName = personaName,
                systemPrompt = (row[2] as String?) ?: "",
                tone = row[3] as String?,
                lengthPref = row[4] as String?,
                createdAt = createdAt,
                userName = (row[6] as String?) ?: "알 수 없음"
            )
        }
    }

    /** 전체 활성 구독(페르소나 연결) 수를 반환한다. */
    override fun countTotalActiveSubscriptions(): Long {
        val sql = "SELECT COUNT(*) FROM batch_categories WHERE persona_id IS NOT NULL AND is_active = TRUE"
        return (em.createNativeQuery(sql).singleResult as Number).toLong()
    }

    /** 프리셋 페르소나를 사용하는 구독이 있는 고유 유저 수를 반환한다. */
    override fun countPresetSubscriptionUsers(): Long {
        val sql = """
            SELECT COUNT(DISTINCT uoc.user_id)
            FROM clipping_user_owned_categories uoc
            JOIN batch_categories c ON c.id = uoc.category_id
            JOIN clipping_personas p ON c.persona_id = p.id
            WHERE p.is_preset = TRUE AND c.is_active = TRUE
        """.trimIndent()
        return (em.createNativeQuery(sql).singleResult as Number).toLong()
    }

    /** 구독이 하나 이상 있는 고유 유저 수를 반환한다. */
    override fun countTotalSubscriptionUsers(): Long {
        val sql = """
            SELECT COUNT(DISTINCT uoc.user_id)
            FROM clipping_user_owned_categories uoc
            JOIN batch_categories c ON c.id = uoc.category_id
            WHERE c.is_active = TRUE
        """.trimIndent()
        return (em.createNativeQuery(sql).singleResult as Number).toLong()
    }

    override fun findToneDistribution(): Map<String, Long> =
        repository.findToneDistribution().mapNotNull { row ->
            val tone = row[0] as? String ?: return@mapNotNull null
            val count = (row[1] as? Number)?.toLong() ?: return@mapNotNull null
            tone to count
        }.toMap()

    override fun findLengthDistribution(): Map<String, Long> =
        repository.findLengthDistribution().mapNotNull { row ->
            val lengthPref = row[0] as? String ?: return@mapNotNull null
            val count = (row[1] as? Number)?.toLong() ?: return@mapNotNull null
            lengthPref to count
        }.toMap()

    private fun coercePersonaCreatedAt(raw: Any?): Instant? =
        when (raw) {
            is Instant -> raw
            is java.sql.Timestamp -> raw.toInstant()
            is java.util.Date -> raw.toInstant()
            else -> null
        }

    private fun PersonaEntity.toModel() = Persona(
        id = id,
        name = name,
        description = description,
        systemPrompt = systemPrompt,
        summaryStyle = summaryStyle,
        targetAudience = targetAudience,
        maxItems = maxItems,
        language = language,
        isActive = isActive,
        isPreset = isPreset,
        previewTitle = previewTitle,
        previewSource = previewSource,
        previewBody = previewBody,
        currentVersion = currentVersion,
        tone = tone,
        lengthPref = lengthPref,
        createdAt = createdAt,
        updatedAt = updatedAt,
        systemUpdatedAt = systemUpdatedAt
    )
}
