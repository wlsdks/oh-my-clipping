package com.clipping.mcpserver.store

import com.clipping.mcpserver.model.SourceCrawlLog
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.core.RowMapper
import org.springframework.stereotype.Repository
import java.time.Instant

/**
 * 소스 크롤 로그를 JDBC로 저장/조회한다.
 * source_crawl_log 테이블에 대한 CRUD를 담당한다.
 */
@Repository
class JdbcSourceCrawlLogStore(private val jdbc: JdbcTemplate) : SourceCrawlLogStore {

    private val rowMapper = RowMapper<SourceCrawlLog> { rs, _ ->
        SourceCrawlLog(
            id = rs.getLong("id"),
            sourceId = rs.getString("source_id"),
            crawledAt = rs.getTimestamp("crawled_at").toInstant(),
            success = rs.getBoolean("success"),
            errorMessage = rs.getString("error_message"),
            responseTimeMs = rs.getInt("response_time_ms").let { if (rs.wasNull()) null else it },
            articlesFound = rs.getInt("articles_found")
        )
    }

    override fun save(log: SourceCrawlLog) {
        // 크롤 결과를 저장한다.
        jdbc.update(
            """
            INSERT INTO source_crawl_log (source_id, crawled_at, success, error_message, response_time_ms, articles_found)
            VALUES (?, ?, ?, ?, ?, ?)
            """.trimIndent(),
            log.sourceId,
            java.sql.Timestamp.from(log.crawledAt),
            log.success,
            log.errorMessage,
            log.responseTimeMs,
            log.articlesFound
        )
    }

    override fun findBySourceId(sourceId: String, cutoff: Instant): List<SourceCrawlLog> =
        // cutoff 이후 로그를 최신 순으로 조회한다.
        jdbc.query(
            """
            SELECT * FROM source_crawl_log
            WHERE source_id = ? AND crawled_at >= ?
            ORDER BY crawled_at DESC
            """.trimIndent(),
            rowMapper,
            sourceId,
            java.sql.Timestamp.from(cutoff)
        )

    override fun getUptimePercent(sourceId: String, cutoff: Instant): Double? {
        // 성공/전체 비율을 계산한다.
        val result = jdbc.queryForMap(
            """
            SELECT COUNT(*) AS total,
                   COALESCE(SUM(CASE WHEN success = TRUE THEN 1 ELSE 0 END), 0) AS success_count
            FROM source_crawl_log
            WHERE source_id = ? AND crawled_at >= ?
            """.trimIndent(),
            sourceId,
            java.sql.Timestamp.from(cutoff)
        )
        val total = (result["total"] as? Number)?.toLong() ?: 0L
        if (total == 0L) return null
        val successCount = (result["success_count"] as? Number)?.toLong() ?: 0L
        return Math.round(successCount * 1000.0 / total) / 10.0
    }

    override fun deleteOlderThan(cutoff: Instant): Int =
        jdbc.update(
            "DELETE FROM source_crawl_log WHERE crawled_at < ?",
            java.sql.Timestamp.from(cutoff)
        )
}
