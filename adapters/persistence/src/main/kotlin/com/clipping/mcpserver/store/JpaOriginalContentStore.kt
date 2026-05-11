package com.clipping.mcpserver.store

import com.clipping.mcpserver.entity.OriginalContentEntity
import com.clipping.mcpserver.model.OriginalContent
import com.clipping.mcpserver.repository.OriginalContentRepository
import jakarta.persistence.EntityManager
import org.springframework.context.annotation.Primary
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.UUID

/**
 * 원본 콘텐츠 JPA 구현. JdbcOriginalContentStore를 대체한다.
 */
@Repository
@Primary
class JpaOriginalContentStore(
    private val repository: OriginalContentRepository,
    private val em: EntityManager
) : OriginalContentStore {

    override fun findByRssItemId(rssItemId: String): OriginalContent? =
        repository.findByRssItemId(rssItemId)?.toModel()

    override fun findBySourceLink(sourceLink: String): OriginalContent? =
        repository.findFirstBySourceLink(sourceLink)?.toModel()

    override fun findByRssItemIds(rssItemIds: Collection<String>): Map<String, OriginalContent> {
        val uniqueIds = rssItemIds.filter { it.isNotBlank() }.distinct()
        if (uniqueIds.isEmpty()) return emptyMap()
        // IN 절 길이를 제한하면서 원본 콘텐츠를 일괄 조회해 export 경로의 N+1 조회를 막는다.
        return uniqueIds.chunked(1000)
            .flatMap { chunk -> repository.findByRssItemIdIn(chunk) }
            .associate { it.rssItemId to it.toModel() }
    }

    override fun findBySourceLinks(sourceLinks: Collection<String>): Map<String, OriginalContent> {
        val uniqueLinks = sourceLinks.filter { it.isNotBlank() }.distinct()
        if (uniqueLinks.isEmpty()) return emptyMap()
        // rss_item_id가 retention으로 사라진 요약도 source_link fallback을 일괄 조회한다.
        return uniqueLinks.chunked(1000)
            .flatMap { chunk -> repository.findBySourceLinkIn(chunk) }
            .associate { it.sourceLink to it.toModel() }
    }

    override fun countByItemOlderThan(cutoff: Instant, categoryId: String?): Int {
        // RSS 아이템의 생성 시각 기준으로 원본 콘텐츠를 카운트한다.
        val sql = if (categoryId != null) {
            """SELECT COUNT(*) FROM original_contents
               WHERE rss_item_id IN (SELECT id FROM rss_items WHERE created_at < ? AND category_id = ?)"""
        } else {
            "SELECT COUNT(*) FROM original_contents WHERE rss_item_id IN (SELECT id FROM rss_items WHERE created_at < ?)"
        }
        val query = em.createNativeQuery(sql)
        query.setParameter(1, java.sql.Timestamp.from(cutoff))
        if (categoryId != null) query.setParameter(2, categoryId)
        return (query.singleResult as Number).toInt()
    }

    override fun save(content: OriginalContent): OriginalContent {
        val now = Instant.now()
        val existing = repository.findByRssItemId(content.rssItemId)

        return if (existing != null) {
            // 기존 콘텐츠 갱신
            existing.sourceLink = content.sourceLink
            existing.title = content.title
            existing.markdown = content.markdown
            existing.contentHash = content.contentHash
            existing.updatedAt = now
            repository.save(existing).toModel()
        } else {
            // 신규 콘텐츠 삽입
            val id = content.id.ifBlank { UUID.randomUUID().toString() }
            val entity = OriginalContentEntity(
                id = id,
                rssItemId = content.rssItemId,
                sourceLink = content.sourceLink,
                title = content.title,
                markdown = content.markdown,
                contentHash = content.contentHash,
                createdAt = now,
                updatedAt = now
            )
            repository.save(entity).toModel()
        }
    }

    @Transactional
    override fun deleteOlderThan(cutoff: Instant): Int =
        repository.deleteByCreatedAtBefore(cutoff)

    private fun OriginalContentEntity.toModel() = OriginalContent(
        id = id,
        rssItemId = rssItemId,
        sourceLink = sourceLink,
        title = title,
        markdown = markdown,
        contentHash = contentHash,
        createdAt = createdAt,
        updatedAt = updatedAt
    )
}
