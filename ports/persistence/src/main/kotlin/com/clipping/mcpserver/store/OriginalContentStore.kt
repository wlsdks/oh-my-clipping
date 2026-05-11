package com.clipping.mcpserver.store

import com.clipping.mcpserver.model.OriginalContent
import java.time.Instant

interface OriginalContentStore {
    fun findByRssItemId(rssItemId: String): OriginalContent?
    fun findBySourceLink(sourceLink: String): OriginalContent?

    /** 여러 RSS 아이템 ID에 연결된 원본 콘텐츠를 한 번에 조회한다. */
    fun findByRssItemIds(rssItemIds: Collection<String>): Map<String, OriginalContent>

    /** 여러 원문 링크에 연결된 원본 콘텐츠를 한 번에 조회한다. */
    fun findBySourceLinks(sourceLinks: Collection<String>): Map<String, OriginalContent>

    fun countByItemOlderThan(cutoff: Instant, categoryId: String? = null): Int
    fun save(content: OriginalContent): OriginalContent

    /** cutoff 이전에 생성된 원본 콘텐츠를 삭제한다. */
    fun deleteOlderThan(cutoff: Instant): Int
}
