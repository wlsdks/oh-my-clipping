package com.clipping.mcpserver.repository

import com.clipping.mcpserver.entity.OriginalContentEntity
import org.springframework.data.jpa.repository.JpaRepository
import java.time.Instant

interface OriginalContentRepository : JpaRepository<OriginalContentEntity, String> {
    fun findByRssItemId(rssItemId: String): OriginalContentEntity?
    fun findFirstBySourceLink(sourceLink: String): OriginalContentEntity?
    fun findByRssItemIdIn(rssItemIds: Collection<String>): List<OriginalContentEntity>
    fun findBySourceLinkIn(sourceLinks: Collection<String>): List<OriginalContentEntity>
    fun deleteByCreatedAtBefore(cutoff: Instant): Int
}
