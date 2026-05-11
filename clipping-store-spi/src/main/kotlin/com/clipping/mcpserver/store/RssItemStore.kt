package com.clipping.mcpserver.store

import com.clipping.mcpserver.model.RssItem
import java.time.Instant

interface RssItemStore {
    fun findById(id: String): RssItem?
    fun findByIds(ids: Collection<String>): List<RssItem>
    fun findUnprocessed(categoryId: String? = null, limit: Int = 1000): List<RssItem>
    fun findByLink(link: String, categoryId: String): RssItem?

    /** 여러 링크의 존재 여부를 일괄 확인하여 이미 저장된 링크 집합을 반환한다. 카테고리 스코프로 필터링된다. */
    fun findExistingLinks(links: Collection<String>, categoryId: String): Set<String>

    fun findByCategoryId(categoryId: String, limit: Int = 1000): List<RssItem>
    fun countOlderThan(cutoff: Instant, categoryId: String? = null): Int

    /**
     * cutoff 이전에 생성된 rss_items 를 삭제한다. limit 가 지정되면 최대 limit 건만 삭제한다.
     *
     * 청크 DELETE 패턴: 스케줄러가 반환값이 0 이 될 때까지 반복 호출해 락 hold 시간을 제어한다.
     * 기존 호출자(categoryId 필터 전용)는 limit 기본값(Int.MAX_VALUE)으로 기존 동작을 유지한다.
     *
     * @param cutoff    created_at < cutoff 인 row 가 대상.
     * @param categoryId null 이면 전체 카테고리 대상.
     * @param limit     한 호출당 최대 삭제 row 수 (기본값 Int.MAX_VALUE = 무제한).
     * @return 실제 삭제된 row 수.
     */
    fun deleteOlderThan(cutoff: Instant, categoryId: String? = null, limit: Int = Int.MAX_VALUE): Int
    fun findUnprocessedIds(categoryId: String, limit: Int): List<String>
    fun updateScreenedScore(id: String, score: Float)
    /** 지정 카테고리에서 cutoff 이후에 생성된 아이템의 제목 목록을 반환한다. */
    fun findRecentTitles(categoryId: String, after: Instant, limit: Int = 500): List<String>

    fun save(item: RssItem): RssItem
    fun markProcessed(id: String)
}
