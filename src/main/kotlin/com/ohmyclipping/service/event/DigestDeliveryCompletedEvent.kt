package com.ohmyclipping.service.event

/**
 * Slack 다이제스트 전송 이후 운영 통계를 후처리하기 위한 이벤트.
 * 핵심 전송 성공 여부와 별개로, 통계 기록 실패가 발송 흐름을 깨지 않게 분리한다.
 */
data class DigestDeliveryCompletedEvent(
    val categoryId: String,
    val sendAttempts: Int,
    val sendSuccesses: Int
)
