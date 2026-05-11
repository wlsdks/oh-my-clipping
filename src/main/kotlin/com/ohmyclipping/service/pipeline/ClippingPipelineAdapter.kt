package com.ohmyclipping.service.pipeline

import com.ohmyclipping.service.ClippingService
import com.ohmyclipping.service.port.ClippingPipelinePort
import com.ohmyclipping.service.port.PipelineCollectResult
import com.ohmyclipping.service.port.PipelineDigestResult
import com.ohmyclipping.service.port.PipelineSummarizeResult
import org.springframework.stereotype.Component

@Component
class ClippingPipelineAdapter(
    private val clippingService: ClippingService
) : ClippingPipelinePort {

    override fun collect(categoryId: String?, hoursBack: Int?): PipelineCollectResult =
        clippingService.collect(categoryId, hoursBack).toPipelineCollectResult()

    override fun summarize(categoryId: String?): PipelineSummarizeResult =
        clippingService.summarize(categoryId).toPipelineSummarizeResult()

    override fun digest(
        categoryId: String,
        maxItems: Int?,
        unsentOnly: Boolean?,
        sendToSlack: Boolean?,
        slackChannelId: String?
    ): PipelineDigestResult =
        clippingService.digest(categoryId, maxItems, unsentOnly, sendToSlack, slackChannelId).toPipelineDigestResult()
}
