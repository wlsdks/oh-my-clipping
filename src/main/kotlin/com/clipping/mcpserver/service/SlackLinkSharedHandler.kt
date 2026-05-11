package com.clipping.mcpserver.service

import io.github.oshai.kotlinlogging.KotlinLogging
import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.MeterRegistry
import org.springframework.dao.DuplicateKeyException
import org.springframework.stereotype.Component

private val log = KotlinLogging.logger {}

/**
 * Slack `link_shared` 이벤트를 처리하는 패시브 공유 핸들러.
 *
 * Slack 에서 다이제스트 URL 이 재공유(forward, copy/paste) 되었을 때 발동한다.
 * URL 이 우리 tracking endpoint 로 판별되면 [UserEventService.savePassiveShare] 로 저장한다.
 *
 * dedup: DB UNIQUE(summary_id, target_channel_id, slack_message_ts) 로 같은 공유의 중복 기록을 방지한다.
 * 같은 메시지에 대한 unfurl event 재발송, forward 등으로 여러 번 들어올 수 있어 [DuplicateKeyException] 은 idempotent 처리.
 *
 * 관측: Micrometer counter 두 개로 매칭/반려 비율을 기록해 capture_rate 측정에 사용한다.
 */
@Component
class SlackLinkSharedHandler(
    private val trackingUrlParser: TrackingUrlParser,
    private val userEventService: UserEventService,
    private val meterRegistry: MeterRegistry,
) {

    private val matchedCounter: Counter = Counter.builder("share.passive.matched")
        .description("Slack link_shared 이벤트 중 우리 tracking URL 로 매칭되어 저장된 건수")
        .register(meterRegistry)

    private val rejectedCounter: Counter = Counter.builder("share.passive.rejected")
        .description("Slack link_shared 이벤트 중 우리 URL 패턴에 매칭되지 않거나 저장 실패로 반려된 건수")
        .register(meterRegistry)

    /**
     * Slack `link_shared` 이벤트 payload 에서 추출된 정보를 처리한다.
     *
     * @param userId 공유한 사용자 Slack user ID
     * @param channelId 공유된 채널 ID (bot 이 초대된 채널만 이벤트 수신)
     * @param messageTs 메시지 timestamp — dedup key 일부
     * @param urls Slack 이 unfurl 대상으로 보낸 URL 문자열 목록
     */
    fun handle(userId: String, channelId: String, messageTs: String, urls: List<String>) {
        // 빠른 guard: 빈 식별자면 저장해도 dedup 이 약해지므로 전부 반려한다.
        if (userId.isBlank() || channelId.isBlank() || messageTs.isBlank()) {
            log.debug {
                "link_shared payload 식별자 누락: userId=$userId channelId=$channelId messageTs=$messageTs"
            }
            rejectedCounter.increment(urls.size.toDouble().coerceAtLeast(0.0))
            return
        }

        for (url in urls) {
            val summaryId = trackingUrlParser.extractSummaryId(url)
            if (summaryId == null) {
                // 우리 tracking URL 이 아닌 일반 링크 — 정상 흐름이며 capture_rate 계산용 denominator.
                rejectedCounter.increment()
                continue
            }
            try {
                userEventService.savePassiveShare(
                    userId = userId,
                    summaryId = summaryId,
                    targetChannelId = channelId,
                    slackMessageTs = messageTs,
                )
                matchedCounter.increment()
            } catch (e: DuplicateKeyException) {
                // 이미 기록된 공유. Slack 은 같은 메시지에 대해 unfurl 재발송 / edit 등으로 여러 번 발송할 수 있다.
                log.debug {
                    "Duplicate passive share ignored: summary=$summaryId channel=$channelId ts=$messageTs"
                }
            } catch (e: Exception) {
                // unexpected — 저장 실패. 상위 Slack dispatch 가 throw 하면 WebSocket 이 끊겨버리니 삼키고 counter 로 기록.
                log.warn(e) { "Failed to record passive share: summary=$summaryId" }
                rejectedCounter.increment()
            }
        }
    }
}
