package com.clipping.mcpserver.observability

import org.springframework.boot.actuate.health.Health
import org.springframework.boot.actuate.health.HealthIndicator
import org.springframework.stereotype.Component
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import java.time.Instant

/**
 * Slack 연결 상태 헬스 인디케이터.
 * SlackTokenValidationScheduler가 매시간 검증한 결과를 공유 AtomicBoolean으로 전달받아
 * /actuator/health 엔드포인트에 Slack 상태를 노출한다.
 */
@Component
class SlackHealthIndicator(
    private val slackHealthStatus: SlackHealthStatus
) : HealthIndicator {

    override fun health(): Health {
        val healthy = slackHealthStatus.isHealthy.get()
        val lastCheck = slackHealthStatus.lastCheckTime.get()

        val details = mutableMapOf<String, Any>()
        if (lastCheck != null) {
            details["lastCheckTime"] = lastCheck.toString()
        }

        return if (healthy) {
            Health.up().withDetails(details).build()
        } else {
            details["reason"] = "Slack token validation failed or not yet checked"
            Health.down().withDetails(details).build()
        }
    }
}

/**
 * Slack 연결 상태를 공유하는 값 객체.
 * SlackTokenValidationScheduler가 결과를 기록하고 SlackHealthIndicator가 읽는다.
 */
@Component
class SlackHealthStatus {
    /** Slack 토큰이 유효한지 여부 */
    val isHealthy = AtomicBoolean(false)
    /** 마지막 검증 시각 */
    val lastCheckTime = AtomicReference<Instant?>(null)
}
