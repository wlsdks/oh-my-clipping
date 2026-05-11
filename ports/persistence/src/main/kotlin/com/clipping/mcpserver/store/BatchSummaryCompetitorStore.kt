package com.clipping.mcpserver.store

import com.clipping.mcpserver.model.BatchSummaryCompetitor
import java.time.Instant

/**
 * batch_summary ↔ competitor 연결 테이블 저장소 인터페이스.
 * 어떤 요약 기사가 어떤 경쟁사와 관련됐는지 추적한다.
 */
interface BatchSummaryCompetitorStore {
    /** 요약 ID와 경쟁사 ID를 연결한다. 중복 시 무시한다. */
    fun link(summaryId: String, competitorId: String)

    /** 요약 ID에 여러 경쟁사를 한 번에 연결한다. */
    fun linkAll(summaryId: String, competitorIds: List<String>)

    /** 특정 요약 ID에 연결된 모든 경쟁사 연결을 반환한다. */
    fun findBySummaryId(summaryId: String): List<BatchSummaryCompetitor>

    /** 여러 요약 ID에 연결된 모든 경쟁사 연결을 일괄 반환한다. N+1 쿼리 방지용. */
    fun findBySummaryIds(summaryIds: List<String>): List<BatchSummaryCompetitor>

    /** 특정 경쟁사 ID에 연결된 요약 목록을 최신 순으로 반환한다. */
    fun findByCompetitorId(competitorId: String, limit: Int = 100): List<BatchSummaryCompetitor>

    /** 특정 경쟁사와 연결된 요약 총 건수를 반환한다. */
    fun countByCompetitorId(competitorId: String): Long

    /** 특정 경쟁사와 연결된 요약 중 since 이후의 건수를 반환한다. */
    fun countByCompetitorIdSince(competitorId: String, since: Instant): Long

    /** 특정 요약 ID에 연결된 모든 경쟁사 연결을 삭제한다. */
    fun deleteBySummaryId(summaryId: String)

    /** 여러 경쟁사에 연결된 요약 ID를 날짜 범위 내에서 최신순으로 반환한다. */
    fun findSummaryIdsByCompetitorIds(
        competitorIds: List<String>,
        from: Instant,
        to: Instant,
        limit: Int = 100
    ): List<String>

    /** 여러 경쟁사에 대해 경쟁사별 연결된 요약 건수를 반환한다. */
    fun countByCompetitorIds(competitorIds: List<String>): Map<String, Long>

    /** 여러 경쟁사에 대해 경쟁사별 기간 내 연결된 요약 건수를 반환한다. */
    fun countByCompetitorIdsSince(
        competitorIds: List<String>,
        since: Instant
    ): Map<String, Long>
}
