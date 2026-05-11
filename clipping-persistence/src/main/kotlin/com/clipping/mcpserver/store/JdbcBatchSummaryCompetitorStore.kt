package com.clipping.mcpserver.store

import com.clipping.mcpserver.model.BatchSummaryCompetitor
import org.springframework.dao.DuplicateKeyException
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.core.RowMapper
import org.springframework.stereotype.Repository
import java.time.Instant

/**
 * batch_summary ↔ competitor 연결 테이블 JDBC 저장소 구현.
 * H2/PostgreSQL 양쪽에서 동작하도록 DuplicateKeyException 방어 패턴을 사용한다.
 */
@Repository
class JdbcBatchSummaryCompetitorStore(private val jdbc: JdbcTemplate) : BatchSummaryCompetitorStore {

    private val rowMapper = RowMapper<BatchSummaryCompetitor> { rs, _ ->
        BatchSummaryCompetitor(
            summaryId = rs.getString("summary_id"),
            competitorId = rs.getString("competitor_id")
        )
    }

    override fun link(summaryId: String, competitorId: String) {
        try {
            // 연결 레코드를 삽입한다. 중복 키 충돌은 조용히 무시한다.
            jdbc.update(
                "INSERT INTO batch_summary_competitors (summary_id, competitor_id) VALUES (?, ?)",
                summaryId, competitorId
            )
        } catch (_: DuplicateKeyException) {
            // 이미 연결된 경우 무시
        }
    }

    override fun linkAll(summaryId: String, competitorIds: List<String>) {
        // 각 경쟁사에 대해 개별 link를 호출하여 중복을 안전하게 처리한다.
        competitorIds.distinct().forEach { link(summaryId, it) }
    }

    override fun findBySummaryId(summaryId: String): List<BatchSummaryCompetitor> =
        jdbc.query(
            "SELECT summary_id, competitor_id FROM batch_summary_competitors WHERE summary_id = ?",
            rowMapper, summaryId
        )

    override fun findBySummaryIds(summaryIds: List<String>): List<BatchSummaryCompetitor> {
        val ids = summaryIds.distinct()
        if (ids.isEmpty()) return emptyList()
        val placeholders = ids.joinToString(",") { "?" }
        return jdbc.query(
            "SELECT summary_id, competitor_id FROM batch_summary_competitors WHERE summary_id IN ($placeholders)",
            rowMapper,
            *ids.toTypedArray()
        )
    }

    override fun findByCompetitorId(competitorId: String, limit: Int): List<BatchSummaryCompetitor> {
        val safeLimit = limit.coerceIn(1, 10000)
        return jdbc.query(
            "SELECT summary_id, competitor_id FROM batch_summary_competitors WHERE competitor_id = ? LIMIT ?",
            rowMapper, competitorId, safeLimit
        )
    }

    override fun countByCompetitorId(competitorId: String): Long =
        jdbc.queryForObject(
            "SELECT COUNT(*) FROM batch_summary_competitors WHERE competitor_id = ?",
            Long::class.java, competitorId
        ) ?: 0L

    override fun countByCompetitorIdSince(competitorId: String, since: Instant): Long =
        jdbc.queryForObject(
            """
            SELECT COUNT(*) FROM batch_summary_competitors bsc
            JOIN batch_summaries bs ON bs.id = bsc.summary_id
            WHERE bsc.competitor_id = ? AND bs.created_at >= ?
            """.trimIndent(),
            Long::class.java,
            competitorId,
            java.sql.Timestamp.from(since)
        ) ?: 0L

    override fun deleteBySummaryId(summaryId: String) {
        jdbc.update(
            "DELETE FROM batch_summary_competitors WHERE summary_id = ?",
            summaryId
        )
    }

    override fun findSummaryIdsByCompetitorIds(
        competitorIds: List<String>,
        from: Instant,
        to: Instant,
        limit: Int
    ): List<String> {
        val ids = competitorIds.distinct()
        if (ids.isEmpty()) return emptyList()
        val safeLimit = limit.coerceIn(1, 1000)
        val placeholders = ids.joinToString(",") { "?" }
        val sql = """
            SELECT bsc.summary_id
            FROM batch_summary_competitors bsc
            JOIN batch_summaries bs ON bs.id = bsc.summary_id
            WHERE bsc.competitor_id IN ($placeholders)
              AND bs.created_at >= ? AND bs.created_at < ?
            GROUP BY bsc.summary_id, bs.created_at
            ORDER BY bs.created_at DESC
            LIMIT ?
        """.trimIndent()
        val params = mutableListOf<Any>()
        params.addAll(ids)
        params += java.sql.Timestamp.from(from)
        params += java.sql.Timestamp.from(to)
        params += safeLimit
        return jdbc.queryForList(sql, String::class.java, *params.toTypedArray())
    }

    override fun countByCompetitorIds(competitorIds: List<String>): Map<String, Long> {
        val ids = competitorIds.distinct()
        if (ids.isEmpty()) return emptyMap()
        val placeholders = ids.joinToString(",") { "?" }
        val sql = """
            SELECT competitor_id, COUNT(*) AS cnt
            FROM batch_summary_competitors
            WHERE competitor_id IN ($placeholders)
            GROUP BY competitor_id
        """.trimIndent()
        return jdbc.query(sql, { rs, _ ->
            rs.getString("competitor_id") to rs.getLong("cnt")
        }, *ids.toTypedArray()).toMap()
    }

    override fun countByCompetitorIdsSince(
        competitorIds: List<String>,
        since: Instant
    ): Map<String, Long> {
        val ids = competitorIds.distinct()
        if (ids.isEmpty()) return emptyMap()
        val placeholders = ids.joinToString(",") { "?" }
        val sql = """
            SELECT bsc.competitor_id, COUNT(*) AS cnt
            FROM batch_summary_competitors bsc
            JOIN batch_summaries bs ON bs.id = bsc.summary_id
            WHERE bsc.competitor_id IN ($placeholders) AND bs.created_at >= ?
            GROUP BY bsc.competitor_id
        """.trimIndent()
        val params = mutableListOf<Any>()
        params.addAll(ids)
        params += java.sql.Timestamp.from(since)
        return jdbc.query(sql, { rs, _ ->
            rs.getString("competitor_id") to rs.getLong("cnt")
        }, *params.toTypedArray()).toMap()
    }
}
