package com.clipping.mcpserver.store

import com.clipping.mcpserver.model.CompetitorWatchlist

/**
 * 경쟁사 워치리스트 저장소 인터페이스.
 * CRUD 및 조건별 조회를 지원한다.
 */
interface CompetitorWatchlistStore {
    fun findAll(): List<CompetitorWatchlist>
    fun findActive(): List<CompetitorWatchlist>
    fun findById(id: String): CompetitorWatchlist?
    fun findByTier(tier: String): List<CompetitorWatchlist>
    /** name 또는 aliases 에 해당 문자열이 포함되면 반환한다 (대소문자 무시). */
    fun findByNameIgnoreCase(name: String): CompetitorWatchlist?

    /**
     * 이름 리스트에 해당하는 경쟁사 목록을 대소문자 무시하여 한 번에 조회한다.
     * 결과는 입력 순서와 무관하며, 존재하지 않는 이름은 결과에 포함되지 않는다.
     * 빈 입력이면 DB 쿼리 없이 빈 리스트를 반환한다.
     */
    fun findByNamesIgnoreCase(names: List<String>): List<CompetitorWatchlist>
    fun save(watchlist: CompetitorWatchlist): CompetitorWatchlist
    fun update(watchlist: CompetitorWatchlist): CompetitorWatchlist
    fun delete(id: String)
}
