package com.clipping.mcpserver.store

import org.springframework.dao.DuplicateKeyException
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.core.RowMapper
import org.springframework.stereotype.Repository
import java.sql.Timestamp
import java.time.Instant

/**
 * summary_cache 테이블의 JDBC 구현.
 * V97 마이그레이션으로 생성된 테이블을 사용한다.
 */
@Repository
class JdbcSummaryCacheStore(
    private val jdbc: JdbcTemplate,
) : SummaryCacheStore {

    private val rowMapper = RowMapper<CachedSummary> { rs, _ ->
        CachedSummary(
            cacheKey = rs.getString("cache_key"),
            summary = rs.getString("summary"),
            keywords = rs.getString("keywords"),
            importanceScore = rs.getFloat("importance_score"),
            sentiment = rs.getString("sentiment"),
            eventType = rs.getString("event_type"),
            translatedTitle = rs.getString("translated_title"),
        )
    }

    override fun findByKey(cacheKey: String): CachedSummary? =
        jdbc.query(
            "SELECT cache_key, summary, keywords, importance_score, sentiment, event_type, translated_title " +
                "FROM summary_cache WHERE cache_key = ?",
            rowMapper,
            cacheKey,
        ).firstOrNull()

    override fun save(entry: CachedSummary) {
        try {
            jdbc.update(
                """
                INSERT INTO summary_cache (cache_key, summary, keywords, importance_score, sentiment, event_type, translated_title)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """.trimIndent(),
                entry.cacheKey,
                entry.summary,
                entry.keywords,
                entry.importanceScore,
                entry.sentiment,
                entry.eventType,
                entry.translatedTitle,
            )
        } catch (_: DuplicateKeyException) {
            // Existing cache wins so concurrent duplicate requests do not spend extra LLM work.
        }
    }

    override fun deleteOlderThan(cutoff: Instant): Int =
        jdbc.update(
            "DELETE FROM summary_cache WHERE created_at < ?",
            Timestamp.from(cutoff),
        )
}
