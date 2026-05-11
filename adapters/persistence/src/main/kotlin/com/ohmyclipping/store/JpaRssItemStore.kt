package com.ohmyclipping.store

import com.ohmyclipping.entity.RssItemEntity
import com.ohmyclipping.model.Language
import com.ohmyclipping.model.RssItem
import com.ohmyclipping.repository.RssItemRepository
import jakarta.persistence.EntityManager
import org.springframework.context.annotation.Primary
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.UUID

/**
 * RSS 아이템 JPA 구현. JdbcRssItemStore를 대체한다.
 */
@Repository
@Primary
class JpaRssItemStore(
    private val repository: RssItemRepository,
    private val em: EntityManager
) : RssItemStore {

    override fun findById(id: String): RssItem? =
        repository.findById(id).orElse(null)?.toModel()

    override fun findByIds(ids: Collection<String>): List<RssItem> {
        if (ids.isEmpty()) return emptyList()
        return repository.findAllById(ids.toList()).map { it.toModel() }
    }

    override fun findUnprocessed(categoryId: String?, limit: Int): List<RssItem> {
        val safeLimit = limit.coerceIn(1, 10000)
        // JPA 쿼리에는 LIMIT이 없으므로 native query를 사용한다.
        val sql = if (categoryId != null) {
            "SELECT * FROM rss_items WHERE is_processed = FALSE AND category_id = ? ORDER BY created_at LIMIT ?"
        } else {
            "SELECT * FROM rss_items WHERE is_processed = FALSE ORDER BY created_at LIMIT ?"
        }
        val query = em.createNativeQuery(sql, RssItemEntity::class.java)
        var idx = 1
        if (categoryId != null) query.setParameter(idx++, categoryId)
        query.setParameter(idx, safeLimit)
        @Suppress("UNCHECKED_CAST")
        return (query.resultList as List<RssItemEntity>).map { it.toModel() }
    }

    override fun findByLink(link: String, categoryId: String): RssItem? =
        repository.findByLinkAndCategoryId(link, categoryId)?.toModel()

    override fun findExistingLinks(links: Collection<String>, categoryId: String): Set<String> {
        if (links.isEmpty()) return emptySet()
        // 대량 링크를 1000개씩 청크로 나누어 IN 절 길이 제한을 회피한다.
        return links.distinct().chunked(1000).flatMap { chunk ->
            repository.findLinksByLinkInAndCategoryId(chunk, categoryId)
        }.toSet()
    }

    override fun findByCategoryId(categoryId: String, limit: Int): List<RssItem> {
        val safeLimit = limit.coerceIn(1, 10000)
        val sql = "SELECT * FROM rss_items WHERE category_id = ? ORDER BY created_at LIMIT ?"
        val query = em.createNativeQuery(sql, RssItemEntity::class.java)
        query.setParameter(1, categoryId)
        query.setParameter(2, safeLimit)
        @Suppress("UNCHECKED_CAST")
        return (query.resultList as List<RssItemEntity>).map { it.toModel() }
    }

    override fun countOlderThan(cutoff: Instant, categoryId: String?): Int =
        if (categoryId != null) {
            repository.countByCategoryIdAndCreatedAtBefore(categoryId, cutoff)
        } else {
            repository.countByCreatedAtBefore(cutoff)
        }

    @Transactional
    override fun deleteOlderThan(cutoff: Instant, categoryId: String?, limit: Int): Int {
        // limit 이 Int.MAX_VALUE 이면 제한 없이 전체 삭제(기존 동작 유지), 그 외엔 native SQL LIMIT 을 적용한다.
        return if (limit == Int.MAX_VALUE) {
            if (categoryId != null) {
                repository.deleteByCategoryIdAndCreatedAtBefore(categoryId, cutoff)
            } else {
                repository.deleteByCreatedAtBefore(cutoff)
            }
        } else {
            val safeLimit = limit.coerceAtLeast(1)
            if (categoryId != null) {
                repository.deleteByCategoryIdAndCreatedAtBeforeLimit(categoryId, cutoff, safeLimit)
            } else {
                repository.deleteByCreatedAtBeforeLimit(cutoff, safeLimit)
            }
        }
    }

    override fun findUnprocessedIds(categoryId: String, limit: Int): List<String> {
        val sql = """
            SELECT id FROM rss_items
            WHERE is_processed = FALSE AND category_id = ?
            ORDER BY created_at
            LIMIT ?
        """.trimIndent()
        val query = em.createNativeQuery(sql)
        query.setParameter(1, categoryId)
        query.setParameter(2, limit)
        @Suppress("UNCHECKED_CAST")
        return query.resultList as List<String>
    }

    @Transactional
    override fun updateScreenedScore(id: String, score: Float) {
        val entity = repository.findById(id).orElse(null) ?: return
        entity.screenedScore = score
        repository.save(entity)
    }

    @Transactional
    override fun markProcessed(id: String) {
        val entity = repository.findById(id).orElse(null) ?: return
        entity.isProcessed = true
        repository.save(entity)
    }

    override fun findRecentTitles(categoryId: String, after: Instant, limit: Int): List<String> {
        val safeLimit = limit.coerceIn(1, 1000)
        val sql = "SELECT title FROM rss_items WHERE category_id = ? AND created_at > ? ORDER BY created_at DESC LIMIT ?"
        val query = em.createNativeQuery(sql)
        query.setParameter(1, categoryId)
        query.setParameter(2, java.sql.Timestamp.from(after))
        query.setParameter(3, safeLimit)
        @Suppress("UNCHECKED_CAST")
        return query.resultList as List<String>
    }

    override fun save(item: RssItem): RssItem {
        val id = item.id.ifBlank { UUID.randomUUID().toString() }
        val now = Instant.now()
        val entity = RssItemEntity(
            id = id,
            title = item.title,
            content = item.content,
            link = item.link,
            publishedAt = item.publishedAt,
            language = item.language.name,
            isProcessed = item.isProcessed,
            categoryId = item.categoryId,
            rssSourceId = item.rssSourceId,
            screenedScore = item.screenedScore,
            createdAt = now
        )
        return repository.save(entity).toModel()
    }

    private fun RssItemEntity.toModel() = RssItem(
        id = id,
        title = title,
        content = content,
        link = link,
        publishedAt = publishedAt,
        language = Language.valueOf(language),
        isProcessed = isProcessed,
        categoryId = categoryId,
        rssSourceId = rssSourceId,
        screenedScore = screenedScore,
        createdAt = createdAt
    )
}
