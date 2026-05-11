package com.ohmyclipping.service.dto.user

/**
 * 사용자 행동 이벤트 수집 요청 DTO.
 * 프론트엔드 EventTracker SDK에서 전송하는 단일 이벤트를 표현한다.
 */
data class UserEventRequest(
    val eventType: String,
    val eventData: Map<String, Any?>?,
    val pagePath: String?,
    val sessionId: String,
    val timestamp: Long
)

/**
 * 사용자 행동 이벤트 일괄 수집 요청 DTO.
 */
data class UserEventBatchRequest(val events: List<UserEventRequest>)

/**
 * 사용자 행동 이벤트 일괄 수집 응답 DTO.
 * 수락된 이벤트 수와 거부된 이벤트 수를 반환한다.
 */
data class UserEventBatchResponse(val accepted: Int, val rejected: Int)
