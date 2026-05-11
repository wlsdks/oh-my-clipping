package com.clipping.mcpserver.observability

import com.clipping.mcpserver.resilience.InMemoryCircuitBreaker
import com.clipping.mcpserver.service.ItemSummarizationService
import io.kotest.matchers.maps.shouldContain
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.boot.actuate.health.Status

/**
 * GeminiApiHealthIndicator 단위 테스트.
 *
 * ItemSummarizationService 가 노출한 [InMemoryCircuitBreaker] 상태를 그대로 Health 로 변환하는
 * 얇은 인디케이터이므로, 서킷 브레이커의 세 가지 상태(CLOSED/HALF_OPEN/OPEN)에 대해 UP/DOWN 전이를 검증한다.
 */
class GeminiApiHealthIndicatorTest {

    private val itemSummarizationService = mockk<ItemSummarizationService>()

    private fun installCircuitBreaker(cb: InMemoryCircuitBreaker): GeminiApiHealthIndicator {
        every { itemSummarizationService.geminiCircuitBreaker } returns cb
        return GeminiApiHealthIndicator(itemSummarizationService)
    }

    private fun newCircuitBreaker(
        failureThreshold: Int = 3,
        resetTimeoutSeconds: Long = 30
    ): InMemoryCircuitBreaker =
        InMemoryCircuitBreaker(
            name = "gemini_test",
            failureThreshold = failureThreshold,
            resetTimeoutSeconds = resetTimeoutSeconds
        )

    @Nested
    inner class `정상 (CLOSED 상태)` {

        @Test
        fun `CLOSED 상태에서는 UP이며 canCall=true가 details에 노출된다`() {
            val cb = newCircuitBreaker()
            val indicator = installCircuitBreaker(cb)

            val health = indicator.health()

            health.status shouldBe Status.UP
            health.details shouldContain ("circuitBreakerState" to "CLOSED")
            health.details shouldContain ("canCall" to true)
        }
    }

    @Nested
    inner class `장애 (OPEN 상태)` {

        @Test
        fun `failureThreshold 도달 시 OPEN이 되고 DOWN을 반환한다`() {
            val cb = newCircuitBreaker(failureThreshold = 2, resetTimeoutSeconds = 300)
            // 연속 실패로 임계값을 채워 OPEN으로 전이시킨다
            cb.recordFailure()
            cb.recordFailure()

            val indicator = installCircuitBreaker(cb)
            val health = indicator.health()

            health.status shouldBe Status.DOWN
            health.details shouldContain ("circuitBreakerState" to "OPEN")
            health.details shouldContain ("canCall" to false)
            (health.details["reason"] as String) shouldBe
                "Circuit breaker is OPEN — Gemini API calls are blocked"
        }

        @Test
        fun `recordSuccess로 복구되면 다시 UP으로 돌아온다`() {
            val cb = newCircuitBreaker(failureThreshold = 1, resetTimeoutSeconds = 300)
            cb.recordFailure()  // OPEN

            val indicator = installCircuitBreaker(cb)
            indicator.health().status shouldBe Status.DOWN

            // recordSuccess는 CLOSED 로 reset 한다 — 구현 명세에 맞춰 검증
            cb.recordSuccess()
            indicator.health().status shouldBe Status.UP
        }
    }
}
