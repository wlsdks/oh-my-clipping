package com.ohmyclipping.store

import java.time.Instant

/**
 * AI 요약 캐시 저장소.
 * 동일 기사+페르소나 조합의 중복 Gemini 호출을 방지한다.
 * 캐시 키는 실제 LLM 입력과 프롬프트 조건을 합친 SHA-256 해시다.
 */
interface SummaryCacheStore {
    /** 캐시 키로 저장된 요약을 조회한다. 없으면 null. */
    fun findByKey(cacheKey: String): CachedSummary?

    /** 요약 결과를 캐시에 저장한다. 이미 존재하는 키면 무시한다. */
    fun save(entry: CachedSummary)

    /** 지정된 시점(cutoff)보다 오래된 캐시 엔트리를 삭제하고 삭제된 건수를 반환한다. */
    fun deleteOlderThan(cutoff: Instant): Int
}

/**
 * 캐시된 AI 요약 결과.
 */
data class CachedSummary(
    val cacheKey: String,
    val summary: String,
    val keywords: String?,
    val importanceScore: Float,
    val sentiment: String?,
    val eventType: String?,
    val translatedTitle: String? = null,
)
