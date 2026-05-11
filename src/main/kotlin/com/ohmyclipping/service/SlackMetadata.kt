package com.ohmyclipping.service

/**
 * Slack 메시지 메타데이터 API에 첨부할 이벤트 타입과 페이로드를 표현한다.
 * sendMessage 포트의 계약 타입이므로 service 패키지에 위치한다.
 */
data class SlackMetadata(
    val eventType: String,
    val eventPayload: Map<String, String> = emptyMap(),
) {
    fun toApiPayload(): Map<String, Any> = mapOf(
        "event_type" to eventType,
        "event_payload" to eventPayload,
    )
}
