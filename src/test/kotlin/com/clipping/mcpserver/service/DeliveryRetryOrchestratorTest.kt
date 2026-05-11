package com.clipping.mcpserver.service

import io.kotest.matchers.shouldBe
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.time.Duration

class DeliveryRetryOrchestratorTest {

    @Nested
    inner class `백오프 계산` {

        @Test
        fun `retry 0 → 2분`() {
            DeliveryRetryOrchestrator.backoffInterval(0) shouldBe Duration.ofMinutes(2)
        }

        @Test
        fun `retry 3 → 30분`() {
            DeliveryRetryOrchestrator.backoffInterval(3) shouldBe Duration.ofMinutes(30)
        }

        @Test
        fun `retry 6 → 4시간`() {
            DeliveryRetryOrchestrator.backoffInterval(6) shouldBe Duration.ofMinutes(240)
        }

        @Test
        fun `retry 7+ → 마지막 간격`() {
            DeliveryRetryOrchestrator.backoffInterval(7) shouldBe Duration.ofMinutes(240)
        }
    }

    @Nested
    inner class `실패 처리` {

        @Test
        fun `retry 6에서 실패 → ABANDONED`() {
            val r = DeliveryRetryOrchestrator.computeFailureOutcome(6, "err")
            r.newRetryCount shouldBe 7
            r.newStatus shouldBe "ABANDONED"
            r.nextRetryAt.shouldBeNull()
        }

        @Test
        fun `retry 2에서 실패 → FAILED + nextRetryAt`() {
            val r = DeliveryRetryOrchestrator.computeFailureOutcome(2, "err")
            r.newRetryCount shouldBe 3
            r.newStatus shouldBe "FAILED"
            r.nextRetryAt.shouldNotBeNull()
        }
    }
}
