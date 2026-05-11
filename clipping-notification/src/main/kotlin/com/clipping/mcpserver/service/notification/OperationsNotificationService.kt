package com.clipping.mcpserver.service.notification

import com.clipping.mcpserver.error.InvalidInputException
import com.clipping.mcpserver.error.ServiceException
import com.clipping.mcpserver.service.port.NotificationChannel
import com.clipping.mcpserver.service.port.NotificationDedupPort
import com.clipping.mcpserver.service.port.NotificationEvent
import com.clipping.mcpserver.service.port.NotificationRuntimeSettingsPort
import com.clipping.mcpserver.service.port.NotificationSeverity
import com.clipping.mcpserver.service.port.OpsNotificationEvent
import com.clipping.mcpserver.service.port.OpsRequestNotificationEvent
import com.clipping.mcpserver.service.port.SourceOpsNotificationPort
import com.clipping.mcpserver.service.port.UserNotificationEvent
import com.clipping.mcpserver.service.port.SlackDeliveryPort
import com.clipping.mcpserver.store.AdminUserStore
import io.github.oshai.kotlinlogging.KotlinLogging
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.util.concurrent.TimeUnit

private val log = KotlinLogging.logger {}

/** Slack 발송 실패 시 파일 기반 폴백 로거. logback-spring.xml에서 ops-alerts.log로 라우팅된다. */
private val opsAlertFallbackLog = LoggerFactory.getLogger("OPS_ALERT_FALLBACK")

/**
 * 알림 발송을 중앙 관리하는 서비스.
 * 모든 운영/사용자 알림은 이 서비스를 통해 전송하며,
 * 중복 방지(dedup), 재시도(retry), 심각도별 prefix를 공통 처리한다.
 * dedup 상태는 Redis에 영속화되어 서버 재시작 후에도 유지된다.
 */
@Service
class OperationsNotificationService(
    private val slackMessageSender: SlackDeliveryPort,
    private val runtimeSettingsPort: NotificationRuntimeSettingsPort,
    private val adminUserStore: AdminUserStore,
    private val notificationDedupPort: NotificationDedupPort
) : SourceOpsNotificationPort {

    companion object {
        private const val DEDUP_KEY_PREFIX = "notif:dedup:"
    }

    /**
     * 운영 채널에 알림을 전송한다.
     * dedup 키가 설정된 이벤트는 중복 발송을 자동 방지한다.
     *
     * @param event 알림 이벤트 (계약 정의)
     * @param message 본문 메시지 (severity prefix는 자동 추가됨)
     * @param params dedup 키 템플릿에 사용할 파라미터 (예: "sourceId" to "src-123")
     */
    fun sendOps(event: OpsNotificationEvent, message: String) {
        sendOps(event, message, emptyMap())
    }

    override fun sendOps(event: OpsNotificationEvent, message: String, params: Map<String, String>) {
        validateNotificationChannel(event, NotificationChannel.OPS, "sendOps")

        val dedupKey = resolveDedupKey(event, params)
        if (dedupKey != null && notificationDedupPort.isDuplicate("$DEDUP_KEY_PREFIX$dedupKey", event.dedupWindowMinutes)) {
            log.debug { "Notification dedup: skipped ${event.eventName} (key=$dedupKey)" }
            return
        }

        val channelId = runtimeSettingsPort.currentNotificationSettings().opsLogChannelId
        if (channelId.isBlank()) {
            log.warn { "Notification skipped: no OPS log channel configured for ${event.eventName}" }
            return
        }

        // 심각도가 INFO인 이벤트는 이미 자체 prefix가 있을 수 있으므로 WARN/CRITICAL만 강제 추가
        val prefixed = if (event.severity != NotificationSeverity.INFO) {
            "${event.severity.prefix}$message"
        } else {
            message
        }

        val sent = sendWithRetry(channelId, prefixed, event.maxRetries)
        if (sent && dedupKey != null) {
            notificationDedupPort.markSent("$DEDUP_KEY_PREFIX$dedupKey", event.dedupWindowMinutes)
        }
    }

    /**
     * 운영 요청 알림 채널에 메시지를 전송한다.
     * opsRequestChannelId 미설정 시 전송을 생략한다.
     */
    fun sendOpsRequest(event: OpsRequestNotificationEvent, message: String) {
        validateNotificationChannel(event, NotificationChannel.OPS_REQUEST, "sendOpsRequest")
        val channelId = runtimeSettingsPort.currentNotificationSettings().opsRequestChannelId
        if (channelId.isBlank()) {
            log.debug { "Notification skipped: no OPS_REQUEST channel configured for ${event.eventName}" }
            return
        }
        // 심각도 prefix를 메시지 앞에 추가한다.
        val prefixed = event.severity.prefix + message
        try {
            slackMessageSender.sendMessage(channelId = channelId, text = prefixed)
        } catch (e: ServiceException) {
            log.warn(e) { "OPS_REQUEST notification failed: ${event.eventName}" }
        }
    }

    /**
     * 사용자에게 Slack DM으로 알림을 전송한다.
     *
     * @param event 알림 이벤트 (계약 정의)
     * @param userId 대상 사용자 ID
     * @param message 본문 메시지
     * @param params dedup 키 템플릿에 사용할 파라미터
     */
    fun sendUserDm(
        event: UserNotificationEvent,
        userId: String,
        message: String,
        params: Map<String, String> = emptyMap()
    ) {
        validateNotificationChannel(event, NotificationChannel.USER_DM, "sendUserDm")

        val dedupKey = resolveDedupKey(event, params)
        if (dedupKey != null && notificationDedupPort.isDuplicate("$DEDUP_KEY_PREFIX$dedupKey", event.dedupWindowMinutes)) return

        val user = adminUserStore.findById(userId)
        if (user == null) {
            log.warn { "Notification skipped: user not found ($userId) for ${event.eventName}" }
            return
        }
        val dmChannelId = user.slackDmChannelId
        if (dmChannelId.isNullOrBlank()) {
            log.debug { "Notification skipped: no DM channel for user $userId" }
            return
        }

        val token = runtimeSettingsPort.currentNotificationSettings().slackBotToken
        if (token.isBlank()) return

        val sent = sendWithRetry(dmChannelId, message, event.maxRetries, token)
        if (sent && dedupKey != null) {
            notificationDedupPort.markSent("$DEDUP_KEY_PREFIX$dedupKey", event.dedupWindowMinutes)
        }
    }

    /**
     * 알림 이벤트의 선언 채널과 호출 경로가 일치하는지 검증한다.
     * 잘못된 조합은 운영/사용자 알림 라우팅을 오염시키므로 전송 전에 거부한다.
     */
    private fun validateNotificationChannel(
        event: NotificationEvent,
        expected: NotificationChannel,
        operation: String
    ) {
        // 호출자가 잘못된 전송 경로를 선택하면 즉시 거부해 알림 라우팅 오염을 막는다.
        if (event.channel != expected) {
            throw InvalidInputException("$operation 는 ${expected.name} 채널 이벤트만 지원한다")
        }
    }

    /**
     * 재시도 포함 메시지 전송. 실패 시 지수 백오프(2s, 4s)로 재시도한다.
     * 모든 재시도 실패 시 파일 기반 폴백 로거로 알림을 기록하여 로그 수집기가 포착할 수 있게 한다.
     * @return 전송 성공 여부
     */
    private fun sendWithRetry(
        channelId: String,
        message: String,
        maxRetries: Int,
        botToken: String? = null
    ): Boolean {
        for (attempt in 0..maxRetries) {
            val result = runCatching {
                slackMessageSender.sendMessage(channelId = channelId, text = message, botToken = botToken)
            }
            if (result.isSuccess) return true
            if (attempt < maxRetries) {
                log.warn { "Notification send failed (attempt ${attempt + 1}/${maxRetries + 1}), retrying..." }
                // 인터럽트 안전한 대기로 종료 신호를 놓치지 않는다
                NotificationRetrySleeper.sleep(
                    delayMs = 2000L * (attempt + 1),
                    context = "OperationsNotificationService retry backoff"
                )
            } else {
                log.error(result.exceptionOrNull()) {
                    "Notification failed after ${maxRetries + 1} attempts: ${message.take(100)}"
                }
                // 모든 Slack 재시도 실패 시 파일 기반 폴백으로 알림을 남긴다
                opsAlertFallbackLog.warn(
                    "[SLACK_FALLBACK] channel={}, message={}",
                    channelId,
                    message.take(500)
                )
            }
        }
        return false
    }

    /** dedup 키 템플릿의 `{paramName}` 플레이스홀더를 실제 값으로 치환한다. */
    internal fun resolveDedupKey(event: NotificationEvent, params: Map<String, String>): String? {
        val template = event.dedupKeyTemplate ?: return null
        var resolved = "${event.eventName}:$template"
        for ((key, value) in params) {
            resolved = resolved.replace("{$key}", value)
        }
        return resolved
    }
}

private object NotificationRetrySleeper {
    fun sleep(delayMs: Long, context: String) {
        if (delayMs <= 0) return
        try {
            TimeUnit.MILLISECONDS.sleep(delayMs)
        } catch (exception: InterruptedException) {
            Thread.currentThread().interrupt()
            throw IllegalStateException("대기 중 인터럽트가 발생했습니다: $context", exception)
        }
    }
}
