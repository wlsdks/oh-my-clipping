package com.ohmyclipping.store

import com.ohmyclipping.model.BatchSummary
import java.time.Instant

/**
 * 사용자 발행기사 히스토리 조회 전용 포트.
 */
interface SentArticleStore {
    /** 발행된 기사를 조건 검색 + 페이지네이션으로 조회한다. */
    fun findSentArticles(
        categoryIds: List<String>,
        keyword: String? = null,
        dateFrom: Instant? = null,
        dateTo: Instant? = null,
        offset: Int = 0,
        limit: Int = 20,
    ): List<BatchSummary>

    /** 발행된 기사 총 건수를 반환한다. */
    fun countSentArticles(
        categoryIds: List<String>,
        keyword: String? = null,
        dateFrom: Instant? = null,
        dateTo: Instant? = null,
    ): Int
}
