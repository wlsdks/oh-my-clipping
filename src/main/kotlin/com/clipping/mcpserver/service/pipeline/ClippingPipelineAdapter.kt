package com.clipping.mcpserver.service.pipeline

import com.clipping.mcpserver.service.ClippingService
import com.clipping.mcpserver.service.port.ClippingPipelinePort
import com.clipping.mcpserver.service.port.PipelineCollectResult
import com.clipping.mcpserver.service.port.PipelineDigestResult
import com.clipping.mcpserver.service.port.PipelineSummarizeResult
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
