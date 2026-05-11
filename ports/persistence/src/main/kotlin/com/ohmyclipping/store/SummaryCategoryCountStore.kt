package com.ohmyclipping.store

import java.time.Instant

/**
 * 날짜 범위 내 카테고리별 요약 수 집계 전용 포트.
 */
interface SummaryCategoryCountStore {
    /** 날짜 범위 내 카테고리별 기사 총 건수를 집계한다. */
    fun countByCategory(from: Instant, to: Instant): Map<String, Int>
}
