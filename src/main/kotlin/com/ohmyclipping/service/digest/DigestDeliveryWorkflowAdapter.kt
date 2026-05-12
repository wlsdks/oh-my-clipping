package com.ohmyclipping.service.digest

import com.ohmyclipping.service.ClippingService
import com.ohmyclipping.service.pipeline.toDigestResult
import com.ohmyclipping.service.pipeline.toPipelineDigestResult
import com.ohmyclipping.service.port.DigestDeliveryWorkflowPort
import com.ohmyclipping.service.port.PipelineDigestResult
import org.springframework.stereotype.Component

@Component
class DigestDeliveryWorkflowAdapter(
    private val clippingService: ClippingService
) : DigestDeliveryWorkflowPort {

    override fun prepareDigest(
        categoryId: String,
        maxItems: Int?,
        unsentOnly: Boolean?,
        sendToSlack: Boolean?,
        slackChannelId: String?,
    ): PipelineDigestResult =
        clippingService.digest(categoryId, maxItems, unsentOnly, sendToSlack, slackChannelId)
            .toPipelineDigestResult()

    override fun sendPreparedDigest(
        categoryId: String,
        preparedDigest: PipelineDigestResult,
        slackChannelId: String,
        categoryNameOverride: String?,
    ): PipelineDigestResult =
        clippingService.sendPreparedDigest(
            categoryId = categoryId,
            preparedDigest = preparedDigest.toDigestResult(),
            slackChannelId = slackChannelId,
            categoryNameOverride = categoryNameOverride,
        ).toPipelineDigestResult()

    override fun finalizePreparedDigest(categoryId: String, preparedDigest: PipelineDigestResult): Int =
        clippingService.finalizePreparedDigest(categoryId, preparedDigest.toDigestResult())
}
