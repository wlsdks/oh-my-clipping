package com.clipping.mcpserver.service.digest

import com.clipping.mcpserver.service.ClippingService
import com.clipping.mcpserver.service.port.DigestDeliveryWorkflowPort
import com.clipping.mcpserver.service.port.PreparedDigestResult
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
    ): PreparedDigestResult =
        clippingService.digest(categoryId, maxItems, unsentOnly, sendToSlack, slackChannelId)
            .toPreparedDigestResult()

    override fun sendPreparedDigest(
        categoryId: String,
        preparedDigest: PreparedDigestResult,
        slackChannelId: String,
        categoryNameOverride: String?,
    ): PreparedDigestResult =
        clippingService.sendPreparedDigest(
            categoryId = categoryId,
            preparedDigest = preparedDigest.toDigestResult(),
            slackChannelId = slackChannelId,
            categoryNameOverride = categoryNameOverride,
        ).toPreparedDigestResult()

    override fun finalizePreparedDigest(categoryId: String, preparedDigest: PreparedDigestResult): Int =
        clippingService.finalizePreparedDigest(categoryId, preparedDigest.toDigestResult())
}
