package com.clipping.mcpserver.observability

import com.clipping.mcpserver.service.ItemSummarizationService
import org.springframework.boot.actuate.health.Health
import org.springframework.boot.actuate.health.HealthIndicator
import org.springframework.stereotype.Component

/**
 * Gemini API 서킷 브레이커 상태 헬스 인디케이터.
 * ItemSummarizationService의 InMemoryCircuitBreaker를 확인하여
 * 서킷이 OPEN이면 DOWN, 그 외는 UP을 반환한다.
 */
@Component
class GeminiApiHealthIndicator(
    private val itemSummarizationService: ItemSummarizationService
) : HealthIndicator {

    override fun health(): Health {
        val cb = itemSummarizationService.geminiCircuitBreaker
        val state = cb.state().name
        val canCall = cb.canCall()

        val details = mapOf(
            "circuitBreakerState" to state,
            "canCall" to canCall
        )

        return if (canCall) {
            Health.up().withDetails(details).build()
        } else {
            Health.down()
                .withDetails(details)
                .withDetail("reason", "Circuit breaker is OPEN — Gemini API calls are blocked")
                .build()
        }
    }
}
