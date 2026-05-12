package com.ohmyclipping.adapter.out.slack

import com.ohmyclipping.service.SlackMessageSender
import com.ohmyclipping.service.SlackMetadata
import com.ohmyclipping.service.port.SlackDeliveryColor
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Component

private val log = KotlinLogging.logger {}

/**
 * OSS 기본 [SlackMessageSender] 구현. 운영 환경에서는 Slack Web API 를 직접 호출하는 구현으로
 * 교체한다 — 자신의 빈을 `@Primary` 로 등록하거나 본 클래스를 fork 에서 대체한다.
 *
 * 본 stub 의 의도:
 * - Spring DI 가 끊기지 않게 빈을 채워 `@SpringBootTest` 컨텍스트 로딩을 가능하게 한다.
 * - `feature flag 꺼짐` 과 동일하게 `ok=false` no-op 결과를 반환해 호출자가 graceful 한 미발송 분기를 타게 한다.
 * - 실제 Slack 통합을 활성화하려면 chat.postMessage, conversations.* 호출 코드와
 *   토큰 검증을 가진 구현체를 별도 빈으로 등록한다.
 *
 * OSS sanitization 으로 원본 구현이 제거됐기 때문에 본 stub 이 필요해졌다. 자세한 맥락은 ADR-044 참고.
 */
@Component
class SlackApiMessageSender : SlackMessageSender {

    init {
        log.warn {
            "SlackApiMessageSender stub is active — Slack delivery is no-op. " +
                "Provide a production Slack client bean to override (see ADR-044)."
        }
    }

    override fun sendMessage(
        channelId: String,
        text: String,
        blocks: List<Map<String, Any?>>,
        threadTs: String?,
        replyBroadcast: Boolean,
        metadata: SlackMetadata?,
        color: SlackDeliveryColor?,
        botToken: String?,
    ): SlackMessageSender.SendResult =
        SlackMessageSender.SendResult(ts = "", channelId = channelId, ok = false)

    override fun testConnection(
        botToken: String?,
        channelId: String?,
    ): SlackMessageSender.SlackConnectionTestResult =
        SlackMessageSender.SlackConnectionTestResult(
            ok = false,
            botUser = null,
            team = null,
            channelId = channelId,
            channelName = null,
            neededScopes = null,
            providedScopes = null,
            rawError = "slack_disabled_stub",
            warning = "OSS stub: provide a production SlackMessageSender bean to enable real delivery.",
        )

    override fun testSocketModeConnection(
        appLevelToken: String?,
    ): SlackMessageSender.SlackSocketModeConnectionTestResult =
        SlackMessageSender.SlackSocketModeConnectionTestResult(
            ok = false,
            appId = null,
            socketUrl = null,
            rawError = "slack_disabled_stub",
            warning = "OSS stub: socket mode disabled.",
        )

    override fun listChannels(
        botToken: String?,
        channelType: String,
    ): List<SlackMessageSender.SlackChannel> = emptyList()

    override fun getChannelInfo(
        botToken: String?,
        channelId: String,
    ): SlackMessageSender.SlackChannel =
        SlackMessageSender.SlackChannel(id = channelId, name = "", isPrivate = false)

    override fun getChannelMembers(
        botToken: String?,
        channelId: String,
    ): Set<String> = emptySet()

    override fun openDmChannel(botToken: String?, memberId: String): String = ""

    override fun updateMessage(
        channelId: String,
        messageTs: String,
        blocks: List<Map<String, Any?>>,
        fallbackText: String,
        color: SlackDeliveryColor?,
        botToken: String?,
    ): SlackMessageSender.SendResult =
        SlackMessageSender.SendResult(
            ts = messageTs,
            channelId = channelId,
            ok = false,
            isNew = false,
        )
}
