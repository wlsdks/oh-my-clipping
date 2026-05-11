package com.ohmyclipping.store

import com.ohmyclipping.model.BatchSummary

/**
 * AI 요약 보강과 fallback 복구 전용 포트.
 *
 * 수집/조회/발송 책임과 분리해 배치 복구 경로가 필요한 저장소 기능만 의존하게 한다.
 */
interface SummaryEnrichmentStore {
    /** summary가 짧은(AI 미요약) 경쟁사 기사를 조회한다. */
    fun findUnsummarizedCompetitorArticles(maxSummaryLength: Int = 200, limit: Int = 50): List<BatchSummary>

    /** 24시간 이내 fallback 요약을 조회한다. 복구 후 재요약 대상. 최대 limit건. */
    fun findFallbacksWithin24h(limit: Int = 200): List<BatchSummary>

    /** AI 요약 결과로 기존 BatchSummary의 요약/키워드/인사이트/감성/이벤트 필드를 업데이트한다. */
    fun updateAiSummary(
        id: String,
        summary: String,
        keywords: String?,
        insights: String?,
        importanceScore: Float,
        sentiment: String?,
        eventType: String?,
    )

    /** 재요약 성공 시 기존 summary 내용을 갱신한다. is_fallback도 FALSE로 변경. */
    fun updateSummaryContent(
        summaryId: String,
        translatedTitle: String?,
        summary: String,
        keywords: List<String>,
        importanceScore: Float,
        sentiment: String?,
        eventType: String?,
    )
}
