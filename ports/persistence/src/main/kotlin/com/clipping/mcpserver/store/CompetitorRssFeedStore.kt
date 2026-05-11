package com.clipping.mcpserver.store

import com.clipping.mcpserver.model.CompetitorRssFeed

/**
 * 경쟁사 수동 RSS 피드 저장소 인터페이스.
 * 경쟁사별로 수동으로 등록한 RSS URL을 관리한다.
 */
interface CompetitorRssFeedStore {
    /** 특정 경쟁사에 연결된 모든 RSS 피드를 반환한다. */
    fun findByCompetitorId(competitorId: String): List<CompetitorRssFeed>

    /** 활성화된 경쟁사의 모든 RSS 피드를 반환한다. */
    fun findAllActive(): List<CompetitorRssFeed>

    /** RSS 피드를 저장하고 저장된 엔티티를 반환한다. id가 비어 있으면 UUID를 생성한다. */
    fun save(feed: CompetitorRssFeed): CompetitorRssFeed

    /** 피드 ID로 삭제한다. */
    fun delete(id: String)

    /** 특정 경쟁사의 모든 피드를 삭제한다. */
    fun deleteByCompetitorId(competitorId: String)

    /** 특정 경쟁사에 연결된 피드 개수를 반환한다. */
    fun countByCompetitorId(competitorId: String): Int
}
