package com.ohmyclipping.store

import com.ohmyclipping.model.LlmRun
import java.time.Instant

interface LlmRunStore {
    fun save(run: LlmRun): LlmRun
    fun findByCreatedAtBetween(from: Instant, to: Instant, categoryId: String? = null): List<LlmRun>

    /** cutoff 이전에 생성된 LLM 실행 기록을 삭제한다. */
    fun deleteOlderThan(cutoff: Instant): Int

    /** 지정 기간 내 총 입력/출력 문자 수를 반환한다. (inputCharsTotal, outputCharsTotal) */
    fun sumCharsBetween(from: Instant, to: Instant): Pair<Long, Long>

    /**
     * 지정 기간 내 비용 계산용 입력/출력 토큰 수를 DB에서 합산한다.
     * tokens_in/out 이 없는 과거 row는 기존 비용 산식과 동일하게 chars/4 추정치를 사용한다.
     */
    fun sumBillableTokensBetween(from: Instant, to: Instant, categoryId: String? = null): Pair<Long, Long>

    /**
     * cutoff 이후 소스별 AI 비용 통계를 반환한다.
     * Map 키: sourceId, 값: (requestCount, tokensIn, tokensOut)
     */
    fun sumTokensBySource(cutoff: Instant): Map<String, Triple<Int, Long, Long>>
}
