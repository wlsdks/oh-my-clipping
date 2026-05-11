package com.clipping.mcpserver.store

import com.clipping.mcpserver.model.BatchSummary
import java.time.Instant

/**
 * 키워드 기반 요약 조회 전용 포트.
 */
interface SummaryKeywordLookupStore {
    /** 키워드 목록 중 하나라도 제목/요약/키워드 필드에 포함된 기사를 조회한다. LIMIT과 정렬 방식을 지정할 수 있다. */
    fun findByKeywordsInRange(
        from: Instant,
        to: Instant,
        keywords: List<String>,
        orderByImportance: Boolean = false,
        limit: Int = 50,
    ): List<BatchSummary>
}
