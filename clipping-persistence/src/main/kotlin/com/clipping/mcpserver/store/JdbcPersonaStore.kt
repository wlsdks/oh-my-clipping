package com.clipping.mcpserver.store

import com.clipping.mcpserver.model.Persona
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.core.RowMapper
import org.springframework.stereotype.Repository
import java.sql.PreparedStatement
import java.time.Instant
import java.util.UUID

@Repository
class JdbcPersonaStore(private val jdbc: JdbcTemplate) : PersonaStore {

    private val rowMapper = RowMapper<Persona> { rs, _ ->
        Persona(
            id = rs.getString("id"),
            name = rs.getString("name"),
            description = rs.getString("description"),
            systemPrompt = rs.getString("system_prompt"),
            summaryStyle = rs.getString("summary_style"),
            targetAudience = rs.getString("target_audience"),
            maxItems = rs.getInt("max_items"),
            language = rs.getString("language") ?: "ko",
            isActive = rs.getBoolean("is_active"),
            isPreset = rs.getBoolean("is_preset"),
            previewTitle = rs.getString("preview_title"),
            previewSource = rs.getString("preview_source"),
            previewBody = rs.getString("preview_body"),
            currentVersion = rs.getInt("current_version"),
            tone = rs.getString("tone"),
            lengthPref = rs.getString("length_pref"),
            createdAt = rs.getTimestamp("created_at").toInstant(),
            updatedAt = rs.getTimestamp("updated_at").toInstant(),
            systemUpdatedAt = rs.getTimestamp("system_updated_at").toInstant()
        )
    }

    override fun list(): List<Persona> =
        jdbc.query("SELECT * FROM clipping_personas ORDER BY created_at", rowMapper)

    override fun listActive(): List<Persona> =
        jdbc.query("SELECT * FROM clipping_personas WHERE is_active = TRUE ORDER BY created_at", rowMapper)

    override fun listPresets(): List<Persona> =
        jdbc.query("SELECT * FROM clipping_personas WHERE is_preset = TRUE AND is_active = TRUE ORDER BY created_at", rowMapper)

    override fun findById(id: String): Persona? =
        jdbc.query("SELECT * FROM clipping_personas WHERE id = ?", rowMapper, id).firstOrNull()

    override fun save(persona: Persona): Persona {
        val now = Instant.now()
        val id = persona.id.ifBlank { UUID.randomUUID().toString() }
        val saved = persona.copy(id = id, createdAt = now, updatedAt = now, systemUpdatedAt = now)
        jdbc.update(
            """INSERT INTO clipping_personas (id, name, description, system_prompt, summary_style,
               target_audience, max_items, language, is_active, is_preset,
               preview_title, preview_source, preview_body,
               current_version, tone, length_pref,
               created_at, updated_at, system_updated_at)
               VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)""",
            saved.id, saved.name, saved.description, saved.systemPrompt, saved.summaryStyle,
            saved.targetAudience, saved.maxItems, saved.language, saved.isActive, saved.isPreset,
            saved.previewTitle, saved.previewSource, saved.previewBody,
            saved.currentVersion, saved.tone, saved.lengthPref,
            java.sql.Timestamp.from(saved.createdAt),
            java.sql.Timestamp.from(saved.updatedAt),
            java.sql.Timestamp.from(saved.systemUpdatedAt)
        )
        return saved
    }

    override fun update(persona: Persona): Persona {
        val now = Instant.now()
        val updated = persona.copy(updatedAt = now, systemUpdatedAt = now)
        jdbc.update(
            """UPDATE clipping_personas SET name = ?, description = ?, system_prompt = ?, summary_style = ?,
               target_audience = ?, max_items = ?, language = ?, is_active = ?, is_preset = ?,
               preview_title = ?, preview_source = ?, preview_body = ?,
               current_version = ?, tone = ?, length_pref = ?,
               updated_at = ?, system_updated_at = ?
               WHERE id = ?""",
            updated.name, updated.description, updated.systemPrompt, updated.summaryStyle,
            updated.targetAudience, updated.maxItems, updated.language, updated.isActive, updated.isPreset,
            updated.previewTitle, updated.previewSource, updated.previewBody,
            updated.currentVersion, updated.tone, updated.lengthPref,
            java.sql.Timestamp.from(updated.updatedAt),
            java.sql.Timestamp.from(updated.systemUpdatedAt),
            updated.id
        )
        return updated
    }

    override fun updateWithExpectedUpdatedAt(persona: Persona, expectedUpdatedAt: Instant): Persona? {
        // 기대 시각 불일치 시 업데이트 실패(affected=0)를 반환해 서비스에서 충돌 예외로 변환한다.
        val now = Instant.now()
        val updated = persona.copy(updatedAt = now, systemUpdatedAt = now)
        val affected = jdbc.update(
            """UPDATE clipping_personas SET name = ?, description = ?, system_prompt = ?, summary_style = ?,
               target_audience = ?, max_items = ?, language = ?, is_active = ?, is_preset = ?,
               preview_title = ?, preview_source = ?, preview_body = ?,
               current_version = ?, tone = ?, length_pref = ?,
               updated_at = ?, system_updated_at = ?
               WHERE id = ? AND updated_at = ?""",
            updated.name, updated.description, updated.systemPrompt, updated.summaryStyle,
            updated.targetAudience, updated.maxItems, updated.language, updated.isActive, updated.isPreset,
            updated.previewTitle, updated.previewSource, updated.previewBody,
            updated.currentVersion, updated.tone, updated.lengthPref,
            java.sql.Timestamp.from(updated.updatedAt),
            java.sql.Timestamp.from(updated.systemUpdatedAt),
            updated.id, java.sql.Timestamp.from(expectedUpdatedAt)
        )
        return if (affected == 1) updated else null
    }

    override fun delete(id: String) {
        jdbc.update("DELETE FROM clipping_personas WHERE id = ?", id)
    }

    override fun countCustomPersonas(): Long =
        jdbc.queryForObject(
            "SELECT COUNT(*) FROM clipping_personas WHERE is_preset = FALSE",
            Long::class.java
        ) ?: 0L

    override fun findPresetUsage(): List<PresetUsageRow> =
        jdbc.query(
            """SELECT p.id, p.name, COUNT(c.id) AS cnt
               FROM clipping_personas p
               LEFT JOIN batch_categories c ON c.persona_id = p.id AND c.is_active = TRUE
               WHERE p.is_preset = TRUE
               GROUP BY p.id, p.name
               ORDER BY cnt DESC"""
        ) { rs, _ ->
            PresetUsageRow(
                presetId = rs.getString("id"),
                presetName = rs.getString("name"),
                activeSubscriptions = rs.getLong("cnt")
            )
        }

    override fun countActiveCustomSubscriptions(): Long =
        jdbc.queryForObject(
            """SELECT COUNT(*) FROM batch_categories c
               JOIN clipping_personas p ON c.persona_id = p.id
               WHERE p.is_preset = FALSE AND c.is_active = TRUE""",
            Long::class.java
        ) ?: 0L

    override fun countActiveSubscriptions(personaId: String): Long =
        jdbc.queryForObject(
            "SELECT COUNT(*) FROM batch_categories WHERE persona_id = ? AND is_active = TRUE",
            Long::class.java,
            personaId
        ) ?: 0L

    override fun findRecentCustomPersonas(limit: Int): List<RecentCustomPersonaRow> {
        val sql = """SELECT p.id, p.name AS persona_name, p.system_prompt,
                      p.tone, p.length_pref, p.created_at,
                      COALESCE(u.display_name, u.username, '알 수 없음') AS user_name
               FROM clipping_personas p
               LEFT JOIN clipping_user_owned_personas o ON o.persona_id = p.id
               LEFT JOIN admin_users u ON u.id = o.user_id
               WHERE p.is_preset = FALSE
               ORDER BY p.created_at DESC
               LIMIT ?"""
        return jdbc.query(
            { con: java.sql.Connection -> con.prepareStatement(sql).also { it.setInt(1, limit) } }
        ) { rs, _ ->
            RecentCustomPersonaRow(
                id = rs.getString("id"),
                userName = rs.getString("user_name") ?: "알 수 없음",
                personaName = rs.getString("persona_name"),
                systemPrompt = rs.getString("system_prompt") ?: "",
                tone = rs.getString("tone"),
                lengthPref = rs.getString("length_pref"),
                createdAt = rs.getTimestamp("created_at").toInstant()
            )
        }
    }

    override fun countTotalActiveSubscriptions(): Long =
        jdbc.queryForObject(
            "SELECT COUNT(*) FROM batch_categories WHERE persona_id IS NOT NULL AND is_active = TRUE",
            Long::class.java
        ) ?: 0L

    override fun countPresetSubscriptionUsers(): Long =
        jdbc.queryForObject(
            """SELECT COUNT(DISTINCT uoc.user_id)
               FROM clipping_user_owned_categories uoc
               JOIN batch_categories c ON c.id = uoc.category_id
               JOIN clipping_personas p ON c.persona_id = p.id
               WHERE p.is_preset = TRUE AND c.is_active = TRUE""",
            Long::class.java
        ) ?: 0L

    override fun countTotalSubscriptionUsers(): Long =
        jdbc.queryForObject(
            """SELECT COUNT(DISTINCT uoc.user_id)
               FROM clipping_user_owned_categories uoc
               JOIN batch_categories c ON c.id = uoc.category_id
               WHERE c.is_active = TRUE""",
            Long::class.java
        ) ?: 0L

    override fun findToneDistribution(): Map<String, Long> =
        jdbc.query(
            "SELECT tone, COUNT(*) AS cnt FROM clipping_personas WHERE is_preset = FALSE AND tone IS NOT NULL GROUP BY tone"
        ) { rs, _ -> rs.getString("tone") to rs.getLong("cnt") }
            .toMap()

    override fun findLengthDistribution(): Map<String, Long> =
        jdbc.query(
            "SELECT length_pref, COUNT(*) AS cnt FROM clipping_personas WHERE is_preset = FALSE AND length_pref IS NOT NULL GROUP BY length_pref"
        ) { rs, _ -> rs.getString("length_pref") to rs.getLong("cnt") }
            .toMap()
}
