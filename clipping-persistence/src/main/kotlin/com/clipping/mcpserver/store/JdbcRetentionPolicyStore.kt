package com.clipping.mcpserver.store

import com.clipping.mcpserver.model.RetentionPolicy
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.core.RowMapper
import org.springframework.stereotype.Repository
import java.time.Instant
import java.util.UUID

@Repository
class JdbcRetentionPolicyStore(private val jdbc: JdbcTemplate) : RetentionPolicyStore {

    private val rowMapper = RowMapper<RetentionPolicy> { rs, _ ->
        RetentionPolicy(
            id = rs.getString("id"),
            categoryId = rs.getString("category_id"),
            keepDays = rs.getInt("keep_days"),
            isEnabled = rs.getBoolean("is_enabled"),
            createdAt = rs.getTimestamp("created_at").toInstant(),
            updatedAt = rs.getTimestamp("updated_at").toInstant()
        )
    }

    override fun findByCategoryId(categoryId: String): RetentionPolicy? =
        jdbc.query(
            "SELECT * FROM clipping_retention_policies WHERE category_id = ?",
            rowMapper,
            categoryId
        ).firstOrNull()

    override fun saveOrUpdate(policy: RetentionPolicy): RetentionPolicy {
        val now = Instant.now()
        val existing = findByCategoryId(policy.categoryId)
        return if (existing != null) {
            val updated = existing.copy(
                keepDays = policy.keepDays,
                isEnabled = policy.isEnabled,
                updatedAt = now
            )
            jdbc.update(
                """
                UPDATE clipping_retention_policies
                SET keep_days = ?, is_enabled = ?, updated_at = ?
                WHERE id = ?
                """.trimIndent(),
                updated.keepDays,
                updated.isEnabled,
                java.sql.Timestamp.from(updated.updatedAt),
                updated.id
            )
            updated
        } else {
            val id = policy.id.ifBlank { UUID.randomUUID().toString() }
            val saved = policy.copy(id = id, createdAt = now, updatedAt = now)
            jdbc.update(
                """
                INSERT INTO clipping_retention_policies
                (id, category_id, keep_days, is_enabled, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?)
                """.trimIndent(),
                saved.id,
                saved.categoryId,
                saved.keepDays,
                saved.isEnabled,
                java.sql.Timestamp.from(saved.createdAt),
                java.sql.Timestamp.from(saved.updatedAt)
            )
            saved
        }
    }
}
