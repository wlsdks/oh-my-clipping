package com.ohmyclipping.store

import com.ohmyclipping.model.ClippingStat
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import org.springframework.dao.DuplicateKeyException
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.core.RowMapper
import org.springframework.stereotype.Repository
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.util.UUID

/**
 * 일 단위 운영 통계(clipping_stats) 저장소 JDBC 구현체.
 */
@Repository
class JdbcStatsStore(private val jdbc: JdbcTemplate) : StatsStore {

    private val mapper = jacksonObjectMapper()

    private val rowMapper = RowMapper<ClippingStat> { rs, _ ->
        val keywordsJson = rs.getString("top_keywords")
        ClippingStat(
            id = rs.getString("id"),
            categoryId = rs.getString("category_id"),
            statDate = rs.getDate("stat_date").toLocalDate(),
            itemsCollected = rs.getInt("items_collected"),
            itemsDuplicates = rs.getInt("items_duplicates"),
            itemsSummarized = rs.getInt("items_summarized"),
            itemsSent = rs.getInt("items_sent"),
            slackSendAttempts = rs.getInt("slack_send_attempts"),
            slackSendSuccesses = rs.getInt("slack_send_successes"),
            topKeywords = parseKeywords(keywordsJson),
            avgImportanceScore = rs.getFloat("avg_importance_score"),
            createdAt = rs.getTimestamp("created_at").toInstant()
        )
    }

    override fun findByCategoryAndDate(categoryId: String, date: LocalDate): ClippingStat? =
        jdbc.query(
            "SELECT * FROM clipping_stats WHERE category_id = ? AND stat_date = ?",
            rowMapper, categoryId, java.sql.Date.valueOf(date)
        ).firstOrNull()

    override fun findMonthly(categoryId: String?, yearMonth: YearMonth): List<ClippingStat> {
        // 월 범위 시작/종료 일자를 계산해 BETWEEN 조회에 사용한다.
        val startDate = yearMonth.atDay(1)
        val endDate = yearMonth.atEndOfMonth()
        return if (categoryId != null) {
            jdbc.query(
                "SELECT * FROM clipping_stats WHERE category_id = ? AND stat_date BETWEEN ? AND ? ORDER BY stat_date",
                rowMapper, categoryId, java.sql.Date.valueOf(startDate), java.sql.Date.valueOf(endDate)
            )
        } else {
            jdbc.query(
                "SELECT * FROM clipping_stats WHERE stat_date BETWEEN ? AND ? ORDER BY stat_date",
                rowMapper, java.sql.Date.valueOf(startDate), java.sql.Date.valueOf(endDate)
            )
        }
    }

    override fun findDailyRange(categoryId: String?, from: LocalDate, to: LocalDate): List<ClippingStat> {
        // categoryId가 있으면 카테고리별, 없으면 전체 일자 범위 통계를 조회한다.
        return if (categoryId != null) {
            jdbc.query(
                "SELECT * FROM clipping_stats WHERE category_id = ? AND stat_date BETWEEN ? AND ? ORDER BY stat_date",
                rowMapper, categoryId, java.sql.Date.valueOf(from), java.sql.Date.valueOf(to)
            )
        } else {
            jdbc.query(
                "SELECT * FROM clipping_stats WHERE stat_date BETWEEN ? AND ? ORDER BY stat_date",
                rowMapper, java.sql.Date.valueOf(from), java.sql.Date.valueOf(to)
            )
        }
    }

    override fun countOlderThan(cutoffDate: LocalDate, categoryId: String?): Int =
        if (categoryId != null) {
            jdbc.queryForObject(
                "SELECT COUNT(*) FROM clipping_stats WHERE stat_date < ? AND category_id = ?",
                Int::class.java,
                java.sql.Date.valueOf(cutoffDate),
                categoryId
            ) ?: 0
        } else {
            jdbc.queryForObject(
                "SELECT COUNT(*) FROM clipping_stats WHERE stat_date < ?",
                Int::class.java,
                java.sql.Date.valueOf(cutoffDate)
            ) ?: 0
        }

    override fun deleteOlderThan(cutoffDate: LocalDate, categoryId: String?): Int =
        if (categoryId != null) {
            jdbc.update(
                "DELETE FROM clipping_stats WHERE stat_date < ? AND category_id = ?",
                java.sql.Date.valueOf(cutoffDate),
                categoryId
            )
        } else {
            jdbc.update(
                "DELETE FROM clipping_stats WHERE stat_date < ?",
                java.sql.Date.valueOf(cutoffDate)
            )
        }

    /**
     * 카테고리/일자 기준 누적 통계를 원자적으로 반영한다.
     */
    override fun upsert(stat: ClippingStat): ClippingStat {
        // 상위 키워드는 공백/중복 제거 후 최대 20개로 정규화한다.
        val normalizedIncomingKeywords = normalizeKeywords(stat.topKeywords)
        // 동일 카테고리+일자 row가 있으면 델타 누적 업데이트를 우선 시도한다.
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
            // 기존 row가 없으면 신규 insert를 수행한다.
            insertStatRecord(saved)
            saved
        } catch (_: DuplicateKeyException) {
            // 동시 insert 경합이면 최신 row를 다시 읽어 델타 업데이트로 보정한다.
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

    private fun insertStatRecord(stat: ClippingStat) {
        jdbc.update(
            """
            INSERT INTO clipping_stats (
                id, category_id, stat_date, items_collected, items_duplicates, items_summarized,
                items_sent, slack_send_attempts, slack_send_successes, top_keywords, avg_importance_score, created_at
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """.trimIndent(),
            stat.id,
            stat.categoryId,
            java.sql.Date.valueOf(stat.statDate),
            stat.itemsCollected,
            stat.itemsDuplicates,
            stat.itemsSummarized,
            stat.itemsSent,
            stat.slackSendAttempts,
            stat.slackSendSuccesses,
            mapper.writeValueAsString(stat.topKeywords),
            stat.avgImportanceScore,
            java.sql.Timestamp.from(stat.createdAt)
        )
    }

    private fun applyDeltaUpdate(stat: ClippingStat, mergedKeywords: List<String>): Int =
        jdbc.update(
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

    private fun requireByCategoryAndDate(categoryId: String, date: LocalDate): ClippingStat =
        findByCategoryAndDate(categoryId, date)
            ?: throw IllegalStateException(
                "Failed to load clipping_stats after upsert for category=$categoryId, date=$date"
            )

    private fun normalizeKeywords(keywords: List<String>): List<String> =
        keywords.map { it.trim() }.filter { it.isNotBlank() }.distinct().take(20)

    private fun mergeKeywords(existing: List<String>, incoming: List<String>): List<String> =
        normalizeKeywords(existing + incoming)

    /** JSON 배열이 아닌 콤마 구분 문자열도 안전하게 파싱한다. */
    private fun parseKeywords(raw: String?): List<String> {
        if (raw.isNullOrBlank()) return emptyList()
        return try {
            mapper.readValue(raw)
        } catch (_: Exception) {
            // JSON 파싱 실패 시 콤마 구분 텍스트로 간주한다.
            raw.split(",").map { it.trim() }.filter { it.isNotBlank() }
        }
    }
}
