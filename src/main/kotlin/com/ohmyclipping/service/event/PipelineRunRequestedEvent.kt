package com.ohmyclipping.service.event

/**
 * 파이프라인 run 레코드가 커밋된 뒤 비동기 실행을 시작하라는 요청 이벤트.
 */
data class PipelineRunRequestedEvent(
    val runId: String,
    val categoryId: String,
    val hoursBack: Int?,
    val maxItems: Int?,
    val unsentOnly: Boolean?,
    val sendToSlack: Boolean?,
    val slackChannelId: String?,
    val ralphLoopEnabled: Boolean?,
    val ralphLoopMaxIterations: Int?,
    val ralphLoopStopPhrase: String?
)
