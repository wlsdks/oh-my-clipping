package com.clipping.mcpserver.store.pipeline

import java.time.Instant
import java.time.LocalDate

/**
 * 파이프라인 분석용 집계 쿼리를 담당하는 스토어 인터페이스.
 * PipelineAnalyticsService가 이 포트에 의존하고, JDBC 구현은 store 패키지에 둔다.
 */
interface PipelineAnalyticsStore {

    /** 기간 내 llm_runs 상태별 건수를 집계한다. [from, to) 반개구간. */
    fun queryLlmStatusCounts(from: Instant, to: Instant): Map<LlmRunStatus, Int>

    /** 기간 내 delivery_log 상태별 건수를 집계한다. [from, to] 폐구간. */
    fun queryDeliveryStatusCounts(from: LocalDate, to: LocalDate): Map<DeliveryLogStatus, Int>

    /** 기간 내 delivery_log를 일자+상태별로 집계하여 날짜 맵으로 반환한다. */
    fun queryDeliveryDailyMap(from: LocalDate, to: LocalDate): Map<LocalDate, DeliveryDayAcc>

    /** 기간 내 EMPTY_RESULT 거절 사유를 error_message 접두어로 분류하여 집계한다. */
    fun queryRejectReasons(from: Instant, to: Instant): Map<String, Int>

    /** 기간 내 카테고리별 발송 통계를 집계한다 (user JOIN 없이 delivery_log 기준). */
    fun queryDeliveryMatrixByCategory(from: LocalDate, to: LocalDate): List<CategoryDeliveryStat>

    /** 카테고리 ID 목록에 대한 소유자(구독자) 맵을 반환한다. */
    fun queryCategoryOwners(categoryIds: List<String>): Map<String, List<CategoryOwner>>
}

/** llm_runs 테이블의 상태 값. DB CHECK 제약과 일치해야 한다. */
enum class LlmRunStatus {
    SUCCEEDED, EMPTY_RESULT, FAILED
}

/** delivery_log 테이블의 상태 값. */
enum class DeliveryLogStatus {
    SENT, SKIPPED, FAILED, RESERVED
}

/** delivery_log 일별 집계 누적기. */
data class DeliveryDayAcc(
    var sent: Int = 0,
    var skipped: Int = 0,
    var failed: Int = 0
)

/** 카테고리별 발송 통계. */
data class CategoryDeliveryStat(
    val categoryId: String,
    val categoryName: String,
    val sent: Int,
    val skipped: Int,
    val failed: Int
)

/** 카테고리 소유자 정보. */
data class CategoryOwner(
    val userId: String,
    val username: String
)
