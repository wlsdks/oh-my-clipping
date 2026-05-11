package com.ohmyclipping.service.dto.admin

/**
 * 파이프라인 실행 요청 파라미터를 묶는 커맨드 객체.
 * PipelineExecutionService.startExecution() 호출 시 10개 파라미터를 1개로 대체한다.
 */
data class PipelineStartCommand(
    val categoryId: String,
    val triggeredBy: String,
    val hoursBack: Int? = null,
    val maxItems: Int? = null,
    val unsentOnly: Boolean? = null,
    val sendToSlack: Boolean? = null,
    val slackChannelId: String? = null,
    val ralphLoopEnabled: Boolean? = null,
    val ralphLoopMaxIterations: Int? = null,
    val ralphLoopStopPhrase: String? = null
)
