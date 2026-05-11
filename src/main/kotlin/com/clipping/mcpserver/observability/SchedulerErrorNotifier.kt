package com.clipping.mcpserver.observability

import com.clipping.mcpserver.service.RuntimeSettingService
import com.clipping.mcpserver.service.port.CollectionBackgroundErrorNotifierPort
import com.clipping.mcpserver.service.port.SlackDeliveryPort
import com.clipping.mcpserver.support.SlackFailureSeverity
import io.github.oshai.kotlinlogging.KotlinLogging
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.concurrent.ConcurrentHashMap

private val log = KotlinLogging.logger {}

/**
 * Slack 발송 실패 시 파일 기반 폴백 로거.
 * logback-spring.xml의 OPS_ALERT_FALLBACK appender 설정에 따라 logs/ops-alerts.log에 기록된다.
 * Slack 자체 장애/토큰 만료로 알림이 유실되지 않도록 이중 안전망 역할을 한다.
 */
private val schedulerFallbackLog = LoggerFactory.getLogger("OPS_ALERT_FALLBACK")

/**
 * 스케줄러 에러 발생 시 Slack 알림을 보내는 컴포넌트.
 * ClippingMetrics.recordSchedulerRun()에서 호출된다.
 *
 * ## severity 기반 라우팅 (F8)
 * - [SlackFailureSeverity.CRITICAL]: dedup을 우회하고 `securityAlertChannelId`로 즉시 발송.
 *   미설정 시 `opsLogChannelId`, 그것도 없으면 환경변수 SLACK_CHANNEL로 폴백.
 * - 그 외 severity: 기존처럼 5분 dedup을 적용하며 errorChannelId(SLACK_CHANNEL)로 발송.
 *
 * ## 파일 폴백 (F12)
 * Slack 자체 발송이 실패(catch)하면 `logs/ops-alerts.log`에 구조화된 문자열로 남긴다.
 * 이중 안전망 덕분에 Slack 토큰 만료 시에도 운영팀이 파일을 확인해 사고를 인지할 수 있다.
 */
@Component
class SchedulerErrorNotifier(
    private val slackMessageSender: SlackDeliveryPort,
    private val runtimeSettingService: RuntimeSettingService,
    private val tokenHealthTracker: TokenHealthTracker,
    @Value("\${SLACK_CHANNEL:}") private val errorChannelId: String
) : CollectionBackgroundErrorNotifierPort {
    private val recentAlerts = ConcurrentHashMap<String, Instant>()

    companion object {
        private const val DEDUP_SECONDS = 300L // 5분
        private val TIME_FMT = DateTimeFormatter.ofPattern("MM-dd HH:mm:ss").withZone(ZoneId.of("Asia/Seoul"))
    }

    /**
     * 범용 백그라운드 에러 알림.
     * context: 어떤 작업에서 발생했는지 (예: "RSS 수집", "Slack 발송", "다이제스트")
     */
    override fun notifyCollectionError(
        context: String,
        exception: Exception,
        extra: String?,
    ) {
        notifySchedulerError(context, exception, extra, SlackFailureSeverity.WARN)
    }

    fun notifyBackgroundError(
        context: String,
        exception: Exception,
        extra: String? = null,
        severity: SlackFailureSeverity = SlackFailureSeverity.WARN
    ) {
        notifySchedulerError(context, exception, extra, severity)
    }

    /**
     * 스케줄러 에러를 Slack과(필요 시) 파일 폴백으로 알린다.
     *
     * @param schedulerName 작업 명칭 — 대시보드/알림 채널 prefix로 사용된다
     * @param exception 발생한 예외. 클래스명·메시지·스택 일부를 알림에 포함한다
     * @param extra 컨텍스트 정보(예: categoryId) — optional
     * @param severity INFO/WARN/CRITICAL. CRITICAL은 dedup 우회 + 전용 채널 라우팅
     */
    fun notifySchedulerError(
        schedulerName: String,
        exception: Exception,
        extra: String? = null,
        severity: SlackFailureSeverity = SlackFailureSeverity.WARN
    ) {
        // CRITICAL은 dedup 우회, 그 외는 기존 dedup 유지
        if (severity != SlackFailureSeverity.CRITICAL && !passDedup(schedulerName, exception)) {
            return
        }

        // 대상 채널 결정: CRITICAL이면 securityAlert 우선, 아니면 errorChannelId
        val channelId = resolveChannel(severity)
        val timestamp = TIME_FMT.format(Instant.now())
        val text = buildMessage(schedulerName, exception, extra, severity, timestamp)

        // Slack 발송을 시도하고 실패 시 파일 폴백에 기록한다.
        val sent = trySendSlack(channelId, text, schedulerName, exception, timestamp, severity)
        if (!sent) {
            writeFileFallback(schedulerName, exception, extra, severity, timestamp)
        }
    }

    /**
     * dedup 윈도우(5분) 내 같은 스케줄러의 같은 에러 유형이 이미 발송됐는지 확인한다.
     * 이미 최근 발송됐으면 false를 돌려 호출자가 생략하게 한다.
     */
    private fun passDedup(schedulerName: String, exception: Exception): Boolean {
        val dedupKey = "$schedulerName:${exception.javaClass.simpleName}"
        val now = Instant.now()
        val last = recentAlerts[dedupKey]
        // 5분 이내 재발생이면 억제한다.
        if (last != null && now.epochSecond - last.epochSecond < DEDUP_SECONDS) return false
        recentAlerts[dedupKey] = now
        return true
    }

    /**
     * severity와 런타임 설정을 근거로 발송 채널을 선택한다.
     * CRITICAL은 securityAlert > opsLog > SLACK_CHANNEL 순, 그 외는 SLACK_CHANNEL 고정.
     */
    private fun resolveChannel(severity: SlackFailureSeverity): String {
        if (severity != SlackFailureSeverity.CRITICAL) return errorChannelId

        val runtime = runtimeSettingService.current()
        // 우선순위: securityAlertChannelId → opsLogChannelId → SLACK_CHANNEL 환경변수
        return runtime.securityAlertChannelId.takeIf { it.isNotBlank() }
            ?: runtime.opsLogChannelId.takeIf { it.isNotBlank() }
            ?: errorChannelId
    }

    /**
     * 알림 메시지 본문을 조립한다.
     * severity별 emoji prefix를 부여해 운영팀이 한눈에 사고 등급을 구분할 수 있게 한다.
     */
    private fun buildMessage(
        schedulerName: String,
        exception: Exception,
        extra: String?,
        severity: SlackFailureSeverity,
        timestamp: String
    ): String {
        val errorMsg = exception.message?.take(200) ?: "no message"
        val stackSnippet = exception.stackTrace
            .take(3)
            .joinToString("\n") { "    at ${it.className}.${it.methodName}(${it.fileName}:${it.lineNumber})" }
        val extraLine = if (extra != null) "\n|• 상세: $extra" else ""
        val (icon, title) = when (severity) {
            SlackFailureSeverity.CRITICAL -> ":rotating_light:" to "CRITICAL 장애"
            SlackFailureSeverity.WARN -> ":warning:" to "백그라운드 에러"
            SlackFailureSeverity.INFO -> ":information_source:" to "백그라운드 알림"
        }
        return """
            |$icon *$title*
            |• 시각: `$timestamp`
            |• 작업: `$schedulerName`
            |• 에러: `${exception.javaClass.simpleName}`
            |• 메시지: $errorMsg$extraLine
            |```
            |$stackSnippet
            |```
        """.trimMargin()
    }

    /**
     * Slack API로 메시지를 전송한다. 토큰/채널 미설정이면 false를 반환해 폴백을 유도한다.
     */
    private fun trySendSlack(
        channelId: String,
        text: String,
        schedulerName: String,
        exception: Exception,
        timestamp: String,
        severity: SlackFailureSeverity
    ): Boolean {
        if (channelId.isBlank()) return false
        return try {
            val botToken = runtimeSettingService.current().slackBotToken
                .takeIf { it.isNotBlank() }
                ?: return false
            slackMessageSender.sendMessage(channelId, text, botToken = botToken)
            true
        } catch (e: Exception) {
            // Slack 자체 장애 또는 토큰 만료 — 파일 폴백 경로로 유도한다.
            log.warn(e) {
                "스케줄러 에러 Slack 알림 발송 실패, 파일 폴백으로 전환 (scheduler=$schedulerName, severity=$severity)"
            }
            false
        }
    }

    /**
     * Slack 발송이 실패했을 때 구조화된 한 줄을 파일 로거로 남긴다.
     * logback `OPS_ALERT_FALLBACK` appender가 `logs/ops-alerts.log`로 라우팅한다.
     */
    private fun writeFileFallback(
        schedulerName: String,
        exception: Exception,
        extra: String?,
        severity: SlackFailureSeverity,
        timestamp: String
    ) {
        val extraPart = if (extra != null) " extra=$extra" else ""
        val msg = exception.message?.take(400) ?: "no message"
        // 파일 폴백은 파싱이 쉽도록 key=value 포맷을 유지한다.
        schedulerFallbackLog.warn(
            "[SCHEDULER_FALLBACK] ts={} severity={} scheduler={} type={} message={}{}",
            timestamp,
            severity.name,
            schedulerName,
            exception.javaClass.simpleName,
            msg,
            extraPart
        )
    }
}
