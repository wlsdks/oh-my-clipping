package com.clipping.mcpserver.service

import com.clipping.mcpserver.observability.ClippingMetrics
import com.clipping.mcpserver.observability.SlackHealthStatus
import io.github.oshai.kotlinlogging.KotlinLogging
import jakarta.annotation.PostConstruct
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.time.Instant

private val log = KotlinLogging.logger {}

/**
 * Slack 봇 토큰의 유효성을 시작 시점과 매시간 검증한다.
 * 토큰이 유효하지 않으면 로그에 경고를 남기고 관리자에게 알린다.
 * 검증 결과를 SlackHealthStatus에 기록하여 /actuator/health에 노출한다.
 */
@Component
@ConditionalOnProperty(
    name = ["clipping.slack.delivery.enabled"],
    havingValue = "true",
    matchIfMissing = true,
)
class SlackTokenValidationScheduler(
    private val slackMessageSender: SlackMessageSender,
    private val runtimeSettingService: RuntimeSettingService,
    private val metrics: ClippingMetrics,
    private val slackHealthStatus: SlackHealthStatus
) {

    /**
     * 애플리케이션 시작 시 Slack 연결을 확인한다.
     * 실패해도 애플리케이션 기동을 중단하지 않는다.
     */
    @PostConstruct
    fun validateOnStartup() {
        runCatching {
            val settings = runtimeSettingService.current()
            val result = slackMessageSender.testConnection(
                botToken = settings.slackBotToken,
                channelId = settings.opsLogChannelId.ifBlank { null }
            )
            // 헬스 인디케이터에 결과를 공유한다
            slackHealthStatus.isHealthy.set(result.ok)
            slackHealthStatus.lastCheckTime.set(Instant.now())
            if (result.ok) {
                log.info { "Slack connection verified: bot=${result.botUser}, team=${result.team}" }
                // 토큰 유효 + 운영 채널 설정 시 테스트 메시지를 보내 실제 발송이 동작하는지 확인한다
                val opsChannelId = settings.opsLogChannelId
                if (opsChannelId.isNotBlank()) {
                    runCatching {
                        slackMessageSender.sendMessage(
                            channelId = opsChannelId,
                            text = ":white_check_mark: 클리핑 서버 시작됨 — Slack 연결 정상"
                        )
                    }.onFailure { e ->
                        log.warn(e) {
                            "Slack startup test message failed — token valid but message delivery may be blocked"
                        }
                    }
                }
            } else {
                log.warn {
                    "Slack connection failed on startup: ${result.rawError ?: "unknown error"}"
                }
            }
        }.onFailure { e ->
            slackHealthStatus.isHealthy.set(false)
            slackHealthStatus.lastCheckTime.set(Instant.now())
            log.warn(e) { "Slack token validation failed on startup" }
        }
    }

    /**
     * 매 정시 실행: Slack 토큰 유효성을 검증한다.
     * 실패 시 Slack이 다운된 상태일 수 있으므로 로그로만 기록한다.
     * 검증 결과를 SlackHealthStatus에 기록하여 /actuator/health에 반영한다.
     */
    @Scheduled(cron = "0 0 * * * *")
    fun validateHourly() = metrics.recordSchedulerRun("slack_token_validation") {
        log.info { "SlackTokenValidationScheduler started" }
        val start = System.nanoTime()
        runCatching {
            val settings = runtimeSettingService.current()
            val result = slackMessageSender.testConnection(
                botToken = settings.slackBotToken,
                channelId = settings.opsLogChannelId.ifBlank { null }
            )
            // 헬스 인디케이터에 결과를 공유한다
            slackHealthStatus.isHealthy.set(result.ok)
            slackHealthStatus.lastCheckTime.set(Instant.now())
            if (!result.ok) {
                log.error {
                    "Slack token validation failed: ${result.rawError ?: "unknown error"}. " +
                        "Slack 메시지 발송이 불가능한 상태입니다."
                }
            }
        }.onFailure { e ->
            slackHealthStatus.isHealthy.set(false)
            slackHealthStatus.lastCheckTime.set(Instant.now())
            log.error(e) { "Slack token validation check failed" }
        }
        val elapsed = (System.nanoTime() - start) / 1_000_000
        log.info { "SlackTokenValidationScheduler completed in ${elapsed}ms" }
    }
}
