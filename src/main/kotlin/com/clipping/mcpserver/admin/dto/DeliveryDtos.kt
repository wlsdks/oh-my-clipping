package com.clipping.mcpserver.admin.dto

import com.clipping.mcpserver.model.DeliveryDaySummary
import com.clipping.mcpserver.model.DeliveryLog

/**
 * 발송 요약 통계 응답 DTO.
 * 특정 날짜의 발송 현황을 집약하여 제공한다.
 */
data class DeliverySummaryResponse(
    val totalCount: Int,
    val sentCount: Int,
    val failedCount: Int,
    val skippedCount: Int,
    val successRate: Double
) {
    companion object {
        /** DeliveryDaySummary를 응답 DTO로 변환한다. */
        fun from(s: DeliveryDaySummary) = DeliverySummaryResponse(
            totalCount = s.totalCount,
            sentCount = s.sentCount,
            failedCount = s.failedCount,
            skippedCount = s.skippedCount,
            successRate = s.successRate
        )
    }
}

/**
 * 발송 이력 단건 응답 DTO.
 * DeliveryLog 엔티티를 API 응답 형태로 변환한다.
 */
data class DeliveryLogResponse(
    val id: String,
    val categoryId: String,
    val channelId: String,
    val deliveryDate: String,
    val deliveryHour: Int,
    val status: String,
    val itemCount: Int,
    val slackMessageTs: String?,
    val retryCount: Int,
    val createdAt: String,
    val updatedAt: String
) {
    companion object {
        /** DeliveryLog 엔티티를 응답 DTO로 변환한다. */
        fun from(log: DeliveryLog) = DeliveryLogResponse(
            id = log.id,
            categoryId = log.categoryId,
            channelId = log.channelId,
            deliveryDate = log.deliveryDate.toString(),
            deliveryHour = log.deliveryHour,
            status = log.status,
            itemCount = log.itemCount,
            slackMessageTs = log.slackMessageTs,
            retryCount = log.retryCount,
            createdAt = log.createdAt.toString(),
            updatedAt = log.updatedAt.toString()
        )
    }
}

/**
 * 발송 이력 페이지네이션 응답 DTO.
 */
data class DeliveryLogsPageResponse(
    val content: List<DeliveryLogResponse>,
    val totalCount: Int,
    val page: Int,
    val size: Int
)
