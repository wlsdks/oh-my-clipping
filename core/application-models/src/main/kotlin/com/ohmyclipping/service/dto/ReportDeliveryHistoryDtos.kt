package com.ohmyclipping.service.dto

/**
 * 리포트 발송 이력 응답.
 * 관리자 UI의 실행 이력 드로어에서 표시한다.
 *
 * @property id 발송 슬롯 고유 ID
 * @property reportType "WEEKLY" | "MONTHLY"
 * @property periodKey 주차/월 키 (예: "2026-W15", "2026-04")
 * @property channelId Slack 채널 ID
 * @property status "RESERVED" | "SENT" | "FAILED"
 * @property durationMs 소요 시간(ms)
 * @property itemsProcessed 처리 건수
 * @property errorMessage 실패 시 에러 메시지
 * @property startedAt 예약 시각(ISO-8601)
 * @property finishedAt 최종 상태 갱신 시각(ISO-8601)
 */
data class ReportDeliveryHistoryItem(
    val id: String,
    val reportType: String,
    val periodKey: String,
    val channelId: String,
    val status: String,
    val durationMs: Long?,
    val itemsProcessed: Int?,
    val errorMessage: String?,
    val startedAt: String,
    val finishedAt: String
)
