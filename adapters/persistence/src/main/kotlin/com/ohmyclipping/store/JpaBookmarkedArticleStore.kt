package com.ohmyclipping.store

import com.ohmyclipping.entity.BookmarkedArticleEntity
import com.ohmyclipping.model.BatchSummary
import com.ohmyclipping.model.BookmarkedArticle
import com.ohmyclipping.repository.BookmarkedArticleRepository
import com.ohmyclipping.support.SqlUtils
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional
import java.sql.ResultSet
import java.sql.Timestamp
import java.time.Instant
import java.util.UUID

private val log = KotlinLogging.logger {}

/**
 * 북마크 스냅샷의 JPA + JdbcTemplate 혼합 구현.
 * 단순 CRUD는 Spring Data JPA, 복합 동적 필터는 JdbcTemplate로 처리한다.
 */
@Repository
class JpaBookmarkedArticleStore(
    private val repository: BookmarkedArticleRepository,
    private val jdbc: JdbcTemplate
) : BookmarkedArticleStore {

    private val mapper = jacksonObjectMapper()

    @Transactional
    override fun toggle(userId: String, source: BatchSummary): Boolean {
        val existing = repository.findByUserIdAndSummaryId(userId, source.id)
        return if (existing != null) {
            // 이미 북마크된 경우 삭제 (원본 summary 상태와 무관).
            repository.deleteByUserIdAndSummaryId(userId, source.id)
            false
        } else {
            // 북마크 생성 시 현재 summary 내용을 그대로 스냅샷한다.
            repository.save(source.toEntity(userId))
            true
        }
    }

    override fun findBookmarkedSummaryIds(
        userId: String,
        summaryIds: List<String>
    ): Set<String> {
        if (summaryIds.isEmpty()) return emptySet()
        return repository.findByUserIdAndSummaryIdIn(userId, summaryIds)
            .map { it.summaryId }
            .toSet()
    }

    override fun findByUserAndSummary(userId: String, summaryId: String): BookmarkedArticle? =
        repository.findByUserIdAndSummaryId(userId, summaryId)?.toModel()

    override fun searchBookmarks(
        userId: String,
        categoryIds: List<String>,
        keyword: String?,
        dateFrom: Instant?,
        dateTo: Instant?,
        offset: Int,
        limit: Int
    ): List<BookmarkedArticle> {
        if (categoryIds.isEmpty()) return emptyList()
        val (where, params) = buildWhere(userId, categoryIds, keyword, dateFrom, dateTo)
        val sql = """
            SELECT * FROM bookmarked_articles
            WHERE $where
            ORDER BY article_created_at DESC
            LIMIT ? OFFSET ?
        """.trimIndent()
        return jdbc.query(sql, { rs, _ -> mapRow(rs) }, *params.toTypedArray(), limit, offset)
    }

    override fun countBookmarks(
        userId: String,
        categoryIds: List<String>,
        keyword: String?,
        dateFrom: Instant?,
        dateTo: Instant?
    ): Int {
        if (categoryIds.isEmpty()) return 0
        val (where, params) = buildWhere(userId, categoryIds, keyword, dateFrom, dateTo)
        val sql = "SELECT COUNT(*) FROM bookmarked_articles WHERE $where"
        return jdbc.queryForObject(sql, Int::class.java, *params.toTypedArray()) ?: 0
    }

    override fun listAllForUser(userId: String): List<BookmarkedArticle> =
        repository.findByUserIdOrderByBookmarkedAtDesc(userId).map { it.toModel() }

    private fun buildWhere(
        userId: String,
        categoryIds: List<String>,
        keyword: String?,
        dateFrom: Instant?,
        dateTo: Instant?
    ): Pair<String, List<Any>> {
        val conditions = mutableListOf<String>()
        val params = mutableListOf<Any>()

        conditions += "user_id = ?"
        params += userId

        val ph = categoryIds.joinToString(",") { "?" }
        conditions += "category_id IN ($ph)"
        params.addAll(categoryIds)

        if (!keyword.isNullOrBlank()) {
            val pattern = "%${SqlUtils.escapeLike(keyword.trim().lowercase())}%"
            conditions += """(
                LOWER(coalesce(original_title, '')) LIKE ?
                OR LOWER(coalesce(translated_title, '')) LIKE ?
                OR LOWER(coalesce(summary, '')) LIKE ?
                OR LOWER(coalesce(keywords, '')) LIKE ?
            )"""
            params.addAll(listOf(pattern, pattern, pattern, pattern))
        }

        if (dateFrom != null) {
            conditions += "article_created_at >= ?"
            params += Timestamp.from(dateFrom)
        }
        if (dateTo != null) {
            conditions += "article_created_at <= ?"
            params += Timestamp.from(dateTo)
        }

        return conditions.joinToString(" AND ") to params
    }

    private fun mapRow(rs: ResultSet): BookmarkedArticle = BookmarkedArticle(
        id = rs.getString("id"),
        userId = rs.getString("user_id"),
        summaryId = rs.getString("summary_id"),
        originalTitle = rs.getString("original_title"),
        translatedTitle = rs.getString("translated_title"),
        summary = rs.getString("summary"),
        insights = rs.getString("insights"),
        keywords = parseKeywords(rs.getString("id"), rs.getString("keywords")),
        importanceScore = rs.getFloat("importance_score"),
        sourceLink = rs.getString("source_link"),
        categoryId = rs.getString("category_id"),
        sentiment = rs.getString("sentiment"),
        eventType = rs.getString("event_type"),
        articleCreatedAt = rs.getTimestamp("article_created_at").toInstant(),
        bookmarkedAt = rs.getTimestamp("bookmarked_at").toInstant()
    )

    private fun parseKeywords(id: String, raw: String?): List<String> {
        if (raw.isNullOrBlank()) return emptyList()
        val trimmed = raw.trim()
        return runCatching {
            if (trimmed.startsWith("[") && trimmed.endsWith("]")) {
                mapper.readValue(trimmed)
            } else {
                trimmed.split(",").map { it.trim() }.filter { it.isNotBlank() }
            }
        }.getOrElse { e ->
            log.warn(e) { "Failed to parse keywords for bookmark=$id, raw=$trimmed" }
            emptyList()
        }
    }

    private fun BookmarkedArticleEntity.toModel() = BookmarkedArticle(
        id = id,
        userId = userId,
        summaryId = summaryId,
        originalTitle = originalTitle,
        translatedTitle = translatedTitle,
        summary = summary,
        insights = insights,
        keywords = parseKeywords(id, keywords),
        importanceScore = importanceScore,
        sourceLink = sourceLink,
        categoryId = categoryId,
        sentiment = sentiment,
        eventType = eventType,
        articleCreatedAt = articleCreatedAt,
        bookmarkedAt = bookmarkedAt
    )

    private fun BatchSummary.toEntity(userId: String) = BookmarkedArticleEntity(
        id = UUID.randomUUID().toString(),
        userId = userId,
        summaryId = id,
        originalTitle = originalTitle,
        translatedTitle = translatedTitle,
        summary = summary,
        insights = insights,
        keywords = if (keywords.isEmpty()) null else mapper.writeValueAsString(keywords),
        importanceScore = importanceScore,
        sourceLink = sourceLink,
        categoryId = categoryId,
        sentiment = sentiment,
        eventType = eventType,
        articleCreatedAt = createdAt,
        bookmarkedAt = Instant.now()
    )
}
