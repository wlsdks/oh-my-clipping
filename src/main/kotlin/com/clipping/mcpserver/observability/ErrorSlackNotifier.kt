package com.clipping.mcpserver.observability

import io.github.oshai.kotlinlogging.KotlinLogging
import com.clipping.mcpserver.service.RuntimeSettingService
import com.clipping.mcpserver.service.port.SlackDeliveryPort
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import org.springframework.web.server.ServerWebExchange
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.concurrent.ConcurrentHashMap

private val log = KotlinLogging.logger {}

/**
 * 서버 에러 발생 시 Slack 채널로 알림을 보내는 컴포넌트.
 * 테스트 기간 동안 유저가 겪는 문제를 즉시 파악하기 위한 용도.
 * 같은 에러가 반복되면 1분 내 중복 알림을 억제한다.
 */
@Component
class ErrorSlackNotifier(
    private val slackMessageSender: SlackDeliveryPort,
    private val runtimeSettingService: RuntimeSettingService,
    @Value("\${SLACK_CHANNEL:}") private val errorChannelId: String
) {
    /** 중복 알림 억제: 에러 키 → 마지막 알림 시각 */
    private val recentAlerts = ConcurrentHashMap<String, Instant>()

    companion object {
        /** 같은 에러에 대한 알림 최소 간격 (초) */
        private const val DEDUP_SECONDS = 60L
        private val TIME_FMT = DateTimeFormatter.ofPattern("MM-dd HH:mm:ss").withZone(ZoneId.of("Asia/Seoul"))
    }

    /**
     * 500 에러 발생 시 Slack 알림을 보낸다.
     */
    fun notifyError(exchange: ServerWebExchange, exception: Exception) {
        if (errorChannelId.isBlank()) return

        try {
            val method = exchange.request.method.name()
            val path = exchange.request.path.pathWithinApplication().value()
            val user = exchange.request.headers.getFirst("X-Forwarded-For")
                ?: exchange.request.remoteAddress?.address?.hostAddress ?: "unknown"

            // 중복 알림 억제
            val dedupKey = "${exception.javaClass.simpleName}:$path"
            val now = Instant.now()
            val last = recentAlerts[dedupKey]
            if (last != null && now.epochSecond - last.epochSecond < DEDUP_SECONDS) {
                return
            }
            recentAlerts[dedupKey] = now

            val errorName = exception.javaClass.simpleName
            val errorMsg = exception.message?.take(200) ?: "no message"
            val timestamp = TIME_FMT.format(now)
            // 스택 트레이스 상위 5줄
            val stackSnippet = exception.stackTrace
                .take(5)
                .joinToString("\n") { "    at ${it.className}.${it.methodName}(${it.fileName}:${it.lineNumber})" }

            val text = """
                |:rotating_light: *서버 에러 발생*
                |• 시각: `$timestamp`
                |• 요청: `$method $path`
                |• 접속 IP: `$user`
                |• 에러: `$errorName`
                |• 메시지: $errorMsg
                |```
                |$stackSnippet
                |```
            """.trimMargin()

            val botToken = runtimeSettingService.current().slackBotToken.takeIf { it.isNotBlank() } ?: return
            slackMessageSender.sendMessage(errorChannelId, text, botToken = botToken)
        } catch (e: Exception) {
            // 알림 실패가 본래 에러 응답을 방해하면 안 된다
            log.warn(e) { "에러 Slack 알림 발송 실패" }
        }
    }
}
