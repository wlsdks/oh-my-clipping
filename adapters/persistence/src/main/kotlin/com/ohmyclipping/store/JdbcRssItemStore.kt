package com.ohmyclipping.store

import com.ohmyclipping.model.Language
import com.ohmyclipping.model.RssItem
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.core.RowMapper
import org.springframework.stereotype.Repository
import java.time.Instant
import java.util.UUID

@Repository
class JdbcRssItemStore(private val jdbc: JdbcTemplate) : RssItemStore {

    private val rowMapper = RowMapper<RssItem> { rs, _ ->
        RssItem(
            id = rs.getString("id"),
            title = rs.getString("title"),
            content = rs.getString("content"),
            link = rs.getString("link"),
            publishedAt = rs.getTimestamp("published_at")?.toInstant(),
            language = Language.valueOf(rs.getString("language")),
            isProcessed = rs.getBoolean("is_processed"),
            categoryId = rs.getString("category_id"),
            rssSourceId = rs.getString("rss_source_id"),
            screenedScore = rs.getFloat("screened_score").takeIf { !rs.wasNull() },
            createdAt = rs.getTimestamp("created_at").toInstant()
        )
    }

    override fun findById(id: String): RssItem? =
        jdbc.query("SELECT * FROM rss_items WHERE id = ?", rowMapper, id).firstOrNull()

    override fun findByIds(ids: Collection<String>): List<RssItem> {
        if (ids.isEmpty()) return emptyList()
        val placeholders = ids.joinToString(",") { "?" }
        val params = ids.toTypedArray()
        return jdbc.query("SELECT * FROM rss_items WHERE id IN ($placeholders)", rowMapper, *params)
    }

    override fun findUnprocessed(categoryId: String?, limit: Int): List<RssItem> {
        val safeLimit = limit.coerceIn(1, 10000)
        return if (categoryId != null) {
            jdbc.query(
                "SELECT * FROM rss_items WHERE is_processed = FALSE AND category_id = ? ORDER BY created_at LIMIT ?",
                rowMapper, categoryId, safeLimit
            )
        } else {
            jdbc.query(
                "SELECT * FROM rss_items WHERE is_processed = FALSE ORDER BY created_at LIMIT ?",
                rowMapper, safeLimit
            )
        }
    }

    override fun findByLink(link: String, categoryId: String): RssItem? =
        jdbc.query(
            "SELECT * FROM rss_items WHERE link = ? AND category_id = ?",
            rowMapper, link, categoryId
        ).firstOrNull()

    override fun findExistingLinks(links: Collection<String>, categoryId: String): Set<String> {
        if (links.isEmpty()) return emptySet()
        // 대량 링크를 1000개씩 청크로 나누어 IN 절 길이 제한을 회피한다.
        return links.distinct().chunked(1000).flatMap { chunk ->
            val placeholders = chunk.joinToString(",") { "?" }
            jdbc.queryForList(
                "SELECT link FROM rss_items WHERE link IN ($placeholders) AND category_id = ?",
                String::class.java,
                *chunk.toTypedArray(),
                categoryId
            )
        }.toSet()
    }

    override fun findByCategoryId(categoryId: String, limit: Int): List<RssItem> {
        val safeLimit = limit.coerceIn(1, 10000)
        return jdbc.query(
            "SELECT * FROM rss_items WHERE category_id = ? ORDER BY created_at LIMIT ?",
            rowMapper, categoryId, safeLimit
        )
    }

    override fun countOlderThan(cutoff: Instant, categoryId: String?): Int {
        val cutoffTs = java.sql.Timestamp.from(cutoff)
        return if (categoryId != null) {
            jdbc.queryForObject(
                "SELECT COUNT(*) FROM rss_items WHERE created_at < ? AND category_id = ?",
                Int::class.java,
                cutoffTs,
                categoryId
            ) ?: 0
        } else {
            jdbc.queryForObject(
                "SELECT COUNT(*) FROM rss_items WHERE created_at < ?",
                Int::class.java,
                cutoffTs
            ) ?: 0
        }
    }

    override fun deleteOlderThan(cutoff: Instant, categoryId: String?, limit: Int): Int {
        val cutoffTs = java.sql.Timestamp.from(cutoff)
        // limit 이 Int.MAX_VALUE 이면 기존 동작(전체 삭제), 그 외엔 서브쿼리 LIMIT 으로 청크 DELETE 한다.
        return if (limit == Int.MAX_VALUE) {
            if (categoryId != null) {
                jdbc.update(
                    "DELETE FROM rss_items WHERE created_at < ? AND category_id = ?",
                    cutoffTs, categoryId
                )
            } else {
                jdbc.update("DELETE FROM rss_items WHERE created_at < ?", cutoffTs)
            }
        } else {
            val safeLimit = limit.coerceAtLeast(1)
            if (categoryId != null) {
                jdbc.update(
                    "DELETE FROM rss_items WHERE id IN " +
                        "(SELECT id FROM rss_items WHERE created_at < ? AND category_id = ? LIMIT ?)",
                    cutoffTs, categoryId, safeLimit
                )
            } else {
                jdbc.update(
                    "DELETE FROM rss_items WHERE id IN " +
                        "(SELECT id FROM rss_items WHERE created_at < ? LIMIT ?)",
                    cutoffTs, safeLimit
                )
            }
        }
    }

    override fun findRecentTitles(categoryId: String, after: Instant, limit: Int): List<String> {
        val safeLimit = limit.coerceIn(1, 1000)
        return jdbc.queryForList(
            "SELECT title FROM rss_items WHERE category_id = ? AND created_at > ? ORDER BY created_at DESC LIMIT ?",
            String::class.java,
            categoryId,
            java.sql.Timestamp.from(after),
            safeLimit
        )
    }

    override fun save(item: RssItem): RssItem {
        val id = item.id.ifBlank { UUID.randomUUID().toString() }
        val saved = item.copy(id = id, createdAt = Instant.now())
        jdbc.update(
            """INSERT INTO rss_items (id, title, content, link, published_at, language, is_processed, category_id, rss_source_id, created_at)
               VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)""",
            saved.id, saved.title, saved.content, saved.link,
            saved.publishedAt?.let { java.sql.Timestamp.from(it) },
            saved.language.name, saved.isProcessed, saved.categoryId, saved.rssSourceId,
            java.sql.Timestamp.from(saved.createdAt)
        )
        return saved
    }

    override fun findUnprocessedIds(categoryId: String, limit: Int): List<String> =
        jdbc.queryForList(
            """SELECT id FROM rss_items
               WHERE is_processed = FALSE AND category_id = ?
               ORDER BY created_at
               LIMIT ?""",
            String::class.java,
            categoryId, limit
        )

    override fun updateScreenedScore(id: String, score: Float) {
        jdbc.update("UPDATE rss_items SET screened_score = ? WHERE id = ?", score, id)
    }

    override fun markProcessed(id: String) {
        jdbc.update("UPDATE rss_items SET is_processed = TRUE WHERE id = ?", id)
    }
}
