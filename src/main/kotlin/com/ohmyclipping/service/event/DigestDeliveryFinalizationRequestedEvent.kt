package com.ohmyclipping.service.event

/**
 * Slack 전송 성공 직후 sent 마킹/통계 기록 후처리를 요청하는 이벤트.
 * 전송 자체는 이미 성공했으므로, 리스너 실패가 상위 호출자에게 전파되지 않도록 분리한다.
 */
data class DigestDeliveryFinalizationRequestedEvent(
    val summaryIds: List<String>,
    val categoryId: String,
    val sendAttempts: Int,
    val sendSuccesses: Int,
    val deliveryLogId: String? = null,
    val slackMessageTs: String? = null
)
