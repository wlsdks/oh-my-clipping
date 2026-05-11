package com.clipping.mcpserver.service.port

/**
 * chat.postMessage / chat.update 발송 결과.
 */
data class SlackDeliveryResult(
    val ts: String,
    val channelId: String,
    val ok: Boolean = true,
    val payloadJson: String = "",
    val isNew: Boolean = true,
    val fallbackUsed: Boolean = false,
)

/**
 * Slack 발송 메타데이터.
 *
 * 서비스/파이프라인 계층은 SlackMessageSender 내부 DTO를 직접 알지 않고 이 포트 DTO만 사용한다.
 */
data class SlackDeliveryMetadata(
    val eventType: String,
    val eventPayload: Map<String, String> = emptyMap(),
)

/**
 * Slack attachment 색상 의도.
 */
enum class SlackDeliveryColor {
    GOOD,
    WARNING,
    DANGER,
    NEUTRAL,
}

/**
 * 배치/파이프라인 서비스가 Slack 발송 어댑터에 접근하는 출력 포트.
 */
interface SlackDeliveryPort {

    /**
     * Slack 채널로 메시지를 전송한다.
     *
     * 연결 검증, 채널 조회, DM 개설 같은 운영/설정 API는 이 포트에 포함하지 않는다.
     */
    fun sendMessage(
        channelId: String,
        text: String,
        blocks: List<Map<String, Any?>> = emptyList(),
        threadTs: String? = null,
        replyBroadcast: Boolean = false,
        metadata: SlackDeliveryMetadata? = null,
        color: SlackDeliveryColor? = null,
        botToken: String? = null,
    ): SlackDeliveryResult

    /**
     * 기존 Slack 메시지의 블록을 업데이트한다.
     */
    fun updateMessage(
        channelId: String,
        messageTs: String,
        blocks: List<Map<String, Any?>>,
        fallbackText: String,
        color: SlackDeliveryColor? = null,
        botToken: String? = null,
    ): SlackDeliveryResult
}
