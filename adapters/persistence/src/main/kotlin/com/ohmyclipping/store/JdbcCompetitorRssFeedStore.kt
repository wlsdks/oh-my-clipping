package com.ohmyclipping.store

import com.ohmyclipping.model.CompetitorRssFeed
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.core.RowMapper
import org.springframework.stereotype.Repository
import java.time.Instant
import java.util.UUID

/**
 * 경쟁사 수동 RSS 피드 JDBC 저장소 구현.
 * findAllActive()는 is_active = TRUE인 경쟁사의 피드만 반환한다.
 */
@Repository
class JdbcCompetitorRssFeedStore(private val jdbc: JdbcTemplate) : CompetitorRssFeedStore {

    private val rowMapper = RowMapper<CompetitorRssFeed> { rs, _ ->
        CompetitorRssFeed(
            id = rs.getString("id"),
            competitorId = rs.getString("competitor_id"),
            feedUrl = rs.getString("feed_url"),
            label = rs.getString("label"),
            createdAt = rs.getTimestamp("created_at").toInstant()
        )
    }

    override fun findByCompetitorId(competitorId: String): List<CompetitorRssFeed> =
        jdbc.query(
            "SELECT * FROM competitor_rss_feeds WHERE competitor_id = ? ORDER BY created_at",
            rowMapper, competitorId
        )

    override fun findAllActive(): List<CompetitorRssFeed> =
        // 활성화된 경쟁사에 연결된 피드만 반환한다.
        jdbc.query(
            """
            SELECT f.*
            FROM competitor_rss_feeds f
            JOIN competitor_watchlist c ON c.id = f.competitor_id
            WHERE c.is_active = TRUE
            ORDER BY f.created_at
            """.trimIndent(),
            rowMapper
        )

    override fun save(feed: CompetitorRssFeed): CompetitorRssFeed {
        val now = Instant.now()
        // id가 비어 있으면 새 UUID를 생성한다.
        val id = feed.id.ifBlank { UUID.randomUUID().toString() }
        val saved = feed.copy(id = id, createdAt = now)
        jdbc.update(
            """INSERT INTO competitor_rss_feeds (id, competitor_id, feed_url, label, created_at)
               VALUES (?, ?, ?, ?, ?)""",
            saved.id, saved.competitorId, saved.feedUrl, saved.label,
            java.sql.Timestamp.from(saved.createdAt)
        )
        return saved
    }

    override fun delete(id: String) {
        jdbc.update("DELETE FROM competitor_rss_feeds WHERE id = ?", id)
    }

    override fun deleteByCompetitorId(competitorId: String) {
        jdbc.update("DELETE FROM competitor_rss_feeds WHERE competitor_id = ?", competitorId)
    }

    override fun countByCompetitorId(competitorId: String): Int =
        jdbc.queryForObject(
            "SELECT COUNT(*) FROM competitor_rss_feeds WHERE competitor_id = ?",
            Int::class.java, competitorId
        ) ?: 0
}
