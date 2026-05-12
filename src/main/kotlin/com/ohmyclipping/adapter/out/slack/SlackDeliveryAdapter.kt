package com.ohmyclipping.adapter.out.slack

import com.ohmyclipping.service.SlackMessageSender
import com.ohmyclipping.service.SlackMetadata
import com.ohmyclipping.service.port.SlackDeliveryColor
import com.ohmyclipping.service.port.SlackDeliveryMetadata
import com.ohmyclipping.service.port.SlackDeliveryPort
import com.ohmyclipping.service.port.SlackDeliveryResult
import org.springframework.stereotype.Component

/**
 * 배치/파이프라인 발송 경로용 [SlackDeliveryPort] 구현. ADR-038 에 따라 `SlackDeliveryPort` 는
 * 발송 전용으로 좁혀져 있고, 본 어댑터가 기존 [SlackMessageSender] 구현에 위임한다. 운영 API
 * (연결 검증, 채널 조회 등) 는 그대로 [SlackMessageSender] 빈을 통해 호출한다.
 */
@Component
class SlackDeliveryAdapter(
    private val slackMessageSender: SlackMessageSender,
) : SlackDeliveryPort {

    override fun sendMessage(
        channelId: String,
        text: String,
        blocks: List<Map<String, Any?>>,
        threadTs: String?,
        replyBroadcast: Boolean,
        metadata: SlackDeliveryMetadata?,
        color: SlackDeliveryColor?,
        botToken: String?,
    ): SlackDeliveryResult {
        val result = slackMessageSender.sendMessage(
            channelId = channelId,
            text = text,
            blocks = blocks,
            threadTs = threadTs,
            replyBroadcast = replyBroadcast,
            metadata = metadata?.let { SlackMetadata(eventType = it.eventType, eventPayload = it.eventPayload) },
            color = color,
            botToken = botToken,
        )
        return SlackDeliveryResult(
            ts = result.ts,
            channelId = result.channelId,
            ok = result.ok,
            payloadJson = result.payloadJson,
            isNew = result.isNew,
            fallbackUsed = result.fallbackUsed,
        )
    }

    override fun updateMessage(
        channelId: String,
        messageTs: String,
        blocks: List<Map<String, Any?>>,
        fallbackText: String,
        color: SlackDeliveryColor?,
        botToken: String?,
    ): SlackDeliveryResult {
        val result = slackMessageSender.updateMessage(
            channelId = channelId,
            messageTs = messageTs,
            blocks = blocks,
            fallbackText = fallbackText,
            color = color,
            botToken = botToken,
        )
        return SlackDeliveryResult(
            ts = result.ts,
            channelId = result.channelId,
            ok = result.ok,
            payloadJson = result.payloadJson,
            isNew = result.isNew,
            fallbackUsed = result.fallbackUsed,
        )
    }
}
