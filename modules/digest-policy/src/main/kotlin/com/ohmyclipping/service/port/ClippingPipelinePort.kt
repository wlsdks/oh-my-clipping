package com.ohmyclipping.service.port

interface ClippingPipelinePort {
    fun collect(categoryId: String?, hoursBack: Int?): PipelineCollectResult

    fun summarize(categoryId: String?): PipelineSummarizeResult

    fun digest(
        categoryId: String,
        maxItems: Int?,
        unsentOnly: Boolean?,
        sendToSlack: Boolean?,
        slackChannelId: String?
    ): PipelineDigestResult
}
