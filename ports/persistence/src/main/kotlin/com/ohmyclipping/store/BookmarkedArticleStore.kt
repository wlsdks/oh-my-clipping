package com.ohmyclipping.store

import com.ohmyclipping.model.BatchSummary
import com.ohmyclipping.model.BookmarkedArticle
import java.time.Instant

/**
 * 북마크된 기사 스냅샷 저장소.
 * 북마크 시점에 BatchSummary 내용을 복사 저장해 원본 retention과 분리한다.
 */
interface BookmarkedArticleStore {
    /**
     * 북마크를 토글한다. 존재하면 삭제, 없으면 [source]의 스냅샷을 새로 생성한다.
     * @return true면 새로 생성됨, false면 삭제됨
     */
    fun toggle(userId: String, source: BatchSummary): Boolean

    /** 후보 요약 ID 집합에서 사용자가 북마크한 것만 반환한다 (목록의 isBookmarked 마킹용). */
    fun findBookmarkedSummaryIds(userId: String, summaryIds: List<String>): Set<String>

    /** 사용자의 특정 summaryId 북마크 스냅샷을 조회한다. 원본이 purge되어도 스냅샷은 유지된다. */
    fun findByUserAndSummary(userId: String, summaryId: String): BookmarkedArticle?

    /**
     * 북마크 목록을 필터링해 페이지 단위로 조회한다.
     * 날짜 필터는 원본 기사 생성 시각(article_created_at)을 기준으로 한다.
     */
    fun searchBookmarks(
        userId: String,
        categoryIds: List<String>,
        keyword: String?,
        dateFrom: Instant?,
        dateTo: Instant?,
        offset: Int,
        limit: Int
    ): List<BookmarkedArticle>

    /** searchBookmarks 와 동일 필터의 전체 건수를 반환한다. */
    fun countBookmarks(
        userId: String,
        categoryIds: List<String>,
        keyword: String?,
        dateFrom: Instant?,
        dateTo: Instant?
    ): Int

    /**
     * 사용자의 모든 북마크를 최신순으로 반환한다.
     * 개인정보 export 등 전수 열람 용도에서만 사용한다.
     */
    fun listAllForUser(userId: String): List<BookmarkedArticle>
}
