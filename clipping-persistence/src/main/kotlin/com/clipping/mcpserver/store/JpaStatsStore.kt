package com.clipping.mcpserver.store

import com.clipping.mcpserver.entity.StatsEntity
import com.clipping.mcpserver.model.ClippingStat
import com.clipping.mcpserver.repository.StatsRepository
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import jakarta.persistence.EntityManager
import org.springframework.context.annotation.Primary
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.util.UUID

/**
 * 일 단위 운영 통계 JPA 구현. JdbcStatsStore를 대체한다.
 * 델타 누적 upsert는 JdbcTemplate을 병용한다.
 */
@Repository
@Primary
class JpaStatsStore(
    private val repository: StatsRepository,
    private val jdbc: JdbcTemplate,
    private val em: EntityManager
) : StatsStore {

    private val mapper = jacksonObjectMapper()

    override fun findByCategoryAndDate(categoryId: String, date: LocalDate): ClippingStat? =
        repository.findByCategoryIdAndStatDate(categoryId, date)?.toModel()

    override fun findMonthly(categoryId: String?, yearMonth: YearMonth): List<ClippingStat> {
        val startDate = yearMonth.atDay(1)
        val endDate = yearMonth.atEndOfMonth()
        val entities = if (categoryId != null) {
            repository.findByCategoryIdAndStatDateBetween(categoryId, startDate, endDate)
        } else {
            repository.findByStatDateBetween(startDate, endDate)
        }
        return entities.map { it.toModel() }.sortedBy { it.statDate }
    }

    override fun findDailyRange(categoryId: String?, from: LocalDate, to: LocalDate): List<ClippingStat> {
        val entities = if (categoryId != null) {
            repository.findByCategoryIdAndStatDateBetween(categoryId, from, to)
        } else {
            repository.findByStatDateBetween(from, to)
        }
        return entities.map { it.toModel() }.sortedBy { it.statDate }
    }

    override fun countOlderThan(cutoffDate: LocalDate, categoryId: String?): Int =
        if (categoryId != null) {
            repository.countByStatDateBeforeAndCategoryId(cutoffDate, categoryId)
        } else {
            repository.countByStatDateBefore(cutoffDate)
        }

    @Transactional
    override fun deleteOlderThan(cutoffDate: LocalDate, categoryId: String?): Int =
        if (categoryId != null) {
            repository.deleteByStatDateBeforeAndCategoryId(cutoffDate, categoryId)
        } else {
            repository.deleteByStatDateBefore(cutoffDate)
        }

    @Transactional
    override fun upsert(stat: ClippingStat): ClippingStat {
        // 키워드 정규화 후 기존 row가 있으면 델타 누적, 없으면 신규 삽입한다.
        val normalizedIncomingKeywords = normalizeKeywords(stat.topKeywords)
        val existing = findByCategoryAndDate(stat.categoryId, stat.statDate)

        if (existing != null) {
            val mergedKeywords = mergeKeywords(existing.topKeywords, normalizedIncomingKeywords)
            val affected = applyDeltaUpdate(stat, mergedKeywords)
            if (affected > 0) {
                return requireByCategoryAndDate(stat.categoryId, stat.statDate)
            }
        }

        val id = stat.id.ifBlank { UUID.randomUUID().toString() }
        val saved = stat.copy(
            id = id,
            topKeywords = normalizedIncomingKeywords,
            createdAt = Instant.now()
        )

        return try {
            repository.save(saved.toEntity())
            saved
        } catch (_: org.springframework.dao.DataIntegrityViolationException) {
            // 동시 insert 경합 시 델타 업데이트로 보정한다.
            val latest = findByCategoryAndDate(stat.categoryId, stat.statDate)
            val mergedKeywords = mergeKeywords(latest?.topKeywords.orEmpty(), normalizedIncomingKeywords)
            val affected = applyDeltaUpdate(stat, mergedKeywords)
            if (affected == 0) {
                throw IllegalStateException(
                    "Failed to upsert clipping_stats for category=${stat.categoryId}, date=${stat.statDate}"
                )
            }
            requireByCategoryAndDate(stat.categoryId, stat.statDate)
        }
    }

    // ── private helpers ──

    /** JdbcTemplate 델타 업데이트 후 JPA 1차 캐시를 비워 stale read를 방지한다. */
    private fun applyDeltaUpdate(stat: ClippingStat, mergedKeywords: List<String>): Int {
        em.flush()
        val affected = jdbc.update(
            """
            UPDATE clipping_stats
            SET items_collected = items_collected + ?,
                items_duplicates = items_duplicates + ?,
                items_summarized = items_summarized + ?,
                items_sent = items_sent + ?,
                slack_send_attempts = slack_send_attempts + ?,
                slack_send_successes = slack_send_successes + ?,
                top_keywords = ?,
                avg_importance_score = CASE WHEN ? > 0 THEN ? ELSE avg_importance_score END
            WHERE category_id = ? AND stat_date = ?
            """.trimIndent(),
            stat.itemsCollected,
            stat.itemsDuplicates,
            stat.itemsSummarized,
            stat.itemsSent,
            stat.slackSendAttempts,
            stat.slackSendSuccesses,
            mapper.writeValueAsString(mergedKeywords),
            stat.avgImportanceScore,
            stat.avgImportanceScore,
            stat.categoryId,
            java.sql.Date.valueOf(stat.statDate)
        )
        em.clear()
        return affected
    }

    private fun requireByCategoryAndDate(categoryId: String, date: LocalDate): ClippingStat =
        findByCategoryAndDate(categoryId, date)
            ?: throw IllegalStateException(
                "Failed to load clipping_stats after upsert for category=$categoryId, date=$date"
            )

    private fun normalizeKeywords(keywords: List<String>): List<String> =
        keywords.map { it.trim() }.filter { it.isNotBlank() }.distinct().take(20)

    private fun mergeKeywords(existing: List<String>, incoming: List<String>): List<String> =
        normalizeKeywords(existing + incoming)

    private fun parseKeywords(raw: String?): List<String> {
        if (raw.isNullOrBlank()) return emptyList()
        return try {
            mapper.readValue(raw)
        } catch (_: Exception) {
            raw.split(",").map { it.trim() }.filter { it.isNotBlank() }
        }
    }

    private fun StatsEntity.toModel() = ClippingStat(
        id = id,
        categoryId = categoryId,
        statDate = statDate,
        itemsCollected = itemsCollected,
        itemsDuplicates = itemsDuplicates,
        itemsSummarized = itemsSummarized,
        itemsSent = itemsSent,
        slackSendAttempts = slackSendAttempts,
        slackSendSuccesses = slackSendSuccesses,
        topKeywords = parseKeywords(topKeywords),
        avgImportanceScore = avgImportanceScore,
        createdAt = createdAt
    )

    private fun ClippingStat.toEntity() = StatsEntity(
        id = id,
        categoryId = categoryId,
        statDate = statDate,
        itemsCollected = itemsCollected,
        itemsDuplicates = itemsDuplicates,
        itemsSummarized = itemsSummarized,
        itemsSent = itemsSent,
        slackSendAttempts = slackSendAttempts,
        slackSendSuccesses = slackSendSuccesses,
        topKeywords = mapper.writeValueAsString(topKeywords),
        avgImportanceScore = avgImportanceScore,
        createdAt = createdAt
    )
}
