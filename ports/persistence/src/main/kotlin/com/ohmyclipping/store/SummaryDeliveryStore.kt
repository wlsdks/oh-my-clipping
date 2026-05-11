package com.ohmyclipping.store

import com.ohmyclipping.model.BatchSummary

/**
 * Slack/digest 발송 상태 조회와 갱신 전용 포트.
 */
interface SummaryDeliveryStore {
    fun findUnsent(categoryId: String? = null, limit: Int = 1000): List<BatchSummary>

    fun markSent(ids: List<String>)

    /** 해당 카테고리에서 Slack 발송 완료된 가장 최근 배치 요약을 반환한다. */
    fun findLatestSentByCategoryId(categoryId: String): BatchSummary?
}
