package com.ohmyclipping.service

import com.ohmyclipping.model.DeliveryDaySummary
import com.ohmyclipping.model.DeliveryLog
import com.ohmyclipping.store.DeliveryLogStore
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.mockk.*
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.LocalDate

class DeliveryAdminServiceTest {

    private val deliveryLogStore = mockk<DeliveryLogStore>()

    private val service = DeliveryAdminService(
        deliveryLogStore = deliveryLogStore
    )

    @Nested
    inner class `retryDelivery 메서드` {

        @Test
        fun `발송 기록이 없으면 NotFoundException을 던진다`() {
            every { deliveryLogStore.findById("missing-log") } returns null

            val exception = shouldThrow<com.ohmyclipping.error.NotFoundException> {
                service.retryDelivery("missing-log")
            }

            exception.message shouldBe "발송 기록을 찾을 수 없습니다: missing-log"
            verify(exactly = 0) { deliveryLogStore.resetForRetry(any()) }
        }

        @Test
        fun `실패 상태가 아닌 SENT 기록이면 InvalidInputException을 던진다`() {
            val log = testDeliveryLog(id = "log-sent", status = "SENT")
            every { deliveryLogStore.findById("log-sent") } returns log

            val exception = shouldThrow<com.ohmyclipping.error.InvalidInputException> {
                service.retryDelivery("log-sent")
            }

            exception.message shouldBe "실패 상태의 발송만 재발송할 수 있습니다"
            verify(exactly = 0) { deliveryLogStore.resetForRetry(any()) }
        }

        @Test
        fun `실패 상태가 아닌 RESERVED 기록이면 InvalidInputException을 던진다`() {
            val log = testDeliveryLog(id = "log-reserved", status = "RESERVED")
            every { deliveryLogStore.findById("log-reserved") } returns log

            val exception = shouldThrow<com.ohmyclipping.error.InvalidInputException> {
                service.retryDelivery("log-reserved")
            }

            exception.message shouldBe "실패 상태의 발송만 재발송할 수 있습니다"
            verify(exactly = 0) { deliveryLogStore.resetForRetry(any()) }
        }

        @Test
        fun `실패 상태가 아닌 SKIPPED 기록이면 InvalidInputException을 던진다`() {
            val log = testDeliveryLog(id = "log-skipped", status = "SKIPPED")
            every { deliveryLogStore.findById("log-skipped") } returns log

            val exception = shouldThrow<com.ohmyclipping.error.InvalidInputException> {
                service.retryDelivery("log-skipped")
            }

            exception.message shouldBe "실패 상태의 발송만 재발송할 수 있습니다"
            verify(exactly = 0) { deliveryLogStore.resetForRetry(any()) }
        }

        @Test
        fun `FAILED 상태 기록이면 retry 플래그를 다시 열어 워커 재시도를 허용한다`() {
            val log = testDeliveryLog(id = "log-failed", status = "FAILED")
            every { deliveryLogStore.findById("log-failed") } returns log
            every { deliveryLogStore.resetForRetry("log-failed") } just Runs

            service.retryDelivery("log-failed")

            verify(exactly = 1) { deliveryLogStore.findById("log-failed") }
            verify(exactly = 1) { deliveryLogStore.resetForRetry("log-failed") }
        }

        @Test
        fun `FINALIZATION_FAILED 상태 기록이면 retry 플래그를 다시 열어 후처리 복구를 허용한다`() {
            val log = testDeliveryLog(id = "log-finalization-failed", status = "FINALIZATION_FAILED")
            every { deliveryLogStore.findById("log-finalization-failed") } returns log
            every { deliveryLogStore.resetForRetry("log-finalization-failed") } just Runs

            service.retryDelivery("log-finalization-failed")

            verify(exactly = 1) { deliveryLogStore.findById("log-finalization-failed") }
            verify(exactly = 1) { deliveryLogStore.resetForRetry("log-finalization-failed") }
        }
    }

    @Nested
    inner class `summary 메서드` {

        @Test
        fun `지정된 날짜를 Store에 그대로 위임한다`() {
            val date = LocalDate.of(2026, 3, 15)
            val expected = DeliveryDaySummary(
                totalCount = 10,
                sentCount = 8,
                failedCount = 1,
                skippedCount = 1,
                successRate = 0.8
            )
            every { deliveryLogStore.summary(date) } returns expected

            val result = service.summary(date)

            result shouldBe expected
            verify(exactly = 1) { deliveryLogStore.summary(date) }
        }
    }

    @Nested
    inner class `findLogs 메서드` {

        @Test
        fun `모든 필터 파라미터를 Store에 그대로 위임한다`() {
            val from = LocalDate.of(2026, 3, 1)
            val to = LocalDate.of(2026, 3, 15)
            val expected = listOf(testDeliveryLog(id = "log-1", status = "SENT"))
            every {
                deliveryLogStore.findAll("cat-1", "SENT", from, to, null, 10, 20)
            } returns expected

            val result = service.findLogs(
                categoryId = "cat-1",
                status = "SENT",
                from = from,
                to = to,
                offset = 10,
                limit = 20
            )

            result shouldBe expected
            verify(exactly = 1) {
                deliveryLogStore.findAll("cat-1", "SENT", from, to, null, 10, 20)
            }
        }

        @Test
        fun `필터가 null이면 null을 그대로 전달한다`() {
            every {
                deliveryLogStore.findAll(null, null, null, null, null, 0, 30)
            } returns emptyList()

            val result = service.findLogs()

            result shouldBe emptyList()
            verify(exactly = 1) {
                deliveryLogStore.findAll(null, null, null, null, null, 0, 30)
            }
        }
    }

    @Nested
    inner class `countLogs 메서드` {

        @Test
        fun `모든 필터 파라미터를 Store에 그대로 위임한다`() {
            val from = LocalDate.of(2026, 3, 1)
            val to = LocalDate.of(2026, 3, 15)
            every {
                deliveryLogStore.countAll("cat-1", "FAILED", from, to, null)
            } returns 5

            val result = service.countLogs(
                categoryId = "cat-1",
                status = "FAILED",
                from = from,
                to = to
            )

            result shouldBe 5
            verify(exactly = 1) {
                deliveryLogStore.countAll("cat-1", "FAILED", from, to, null)
            }
        }

        @Test
        fun `필터가 null이면 null을 그대로 전달한다`() {
            every {
                deliveryLogStore.countAll(null, null, null, null, null)
            } returns 42

            val result = service.countLogs()

            result shouldBe 42
            verify(exactly = 1) {
                deliveryLogStore.countAll(null, null, null, null, null)
            }
        }
    }

    private fun testDeliveryLog(id: String, status: String): DeliveryLog =
        DeliveryLog(
            id = id,
            categoryId = "cat-1",
            channelId = "C_TEST",
            deliveryDate = LocalDate.of(2026, 3, 15),
            deliveryHour = 9,
            status = status,
            itemCount = 5,
            slackMessageTs = null,
                    createdAt = Instant.now(),
            updatedAt = Instant.now()
        )
}
