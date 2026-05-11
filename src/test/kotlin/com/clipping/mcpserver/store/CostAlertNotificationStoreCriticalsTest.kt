package com.clipping.mcpserver.store

import com.clipping.mcpserver.entity.CostAlertNotificationEntity
import com.clipping.mcpserver.repository.CostAlertNotificationRepository
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

/**
 * CostAlertNotificationStore.findActiveCriticalsByMonth() 단위 테스트.
 * CRITICAL_ 접두어 필터링과 다른 레벨(DAILY_COST 등) 제외를 검증한다.
 */
class CostAlertNotificationStoreCriticalsTest {

    private val repo = mockk<CostAlertNotificationRepository>()
    private val store = JpaCostAlertNotificationStore(repo)

    @Nested
    inner class `findActiveCriticalsByMonth 메서드` {

        @Test
        fun `CRITICAL_ 접두어를 가진 엔티티만 반환한다`() {
            val critical90 = CostAlertNotificationEntity(monthId = "2026-04", thresholdLevel = "CRITICAL_90")
            val critical100 = CostAlertNotificationEntity(monthId = "2026-04", thresholdLevel = "CRITICAL_100")

            every {
                repo.findByMonthIdAndThresholdLevelStartingWith("2026-04", "CRITICAL_")
            } returns listOf(critical90, critical100)

            val result = store.findActiveCriticalsByMonth("2026-04")

            result shouldBe listOf(
                CostAlertNotification(
                    monthId = critical90.monthId,
                    thresholdLevel = critical90.thresholdLevel,
                    notifiedAt = critical90.notifiedAt,
                ),
                CostAlertNotification(
                    monthId = critical100.monthId,
                    thresholdLevel = critical100.thresholdLevel,
                    notifiedAt = critical100.notifiedAt,
                ),
            )
        }

        @Test
        fun `DAILY_COST 접두어 엔티티는 포함하지 않는다`() {
            // DAILY_COST_ 는 CRITICAL_ 접두어가 아니므로 레포지토리가 반환하지 않는다.
            every {
                repo.findByMonthIdAndThresholdLevelStartingWith("2026-04", "CRITICAL_")
            } returns emptyList()

            val result = store.findActiveCriticalsByMonth("2026-04")

            result shouldBe emptyList()
            verify(exactly = 1) {
                repo.findByMonthIdAndThresholdLevelStartingWith("2026-04", "CRITICAL_")
            }
        }

        @Test
        fun `CRITICAL_ 엔티티가 없으면 빈 리스트를 반환한다`() {
            every {
                repo.findByMonthIdAndThresholdLevelStartingWith("2026-03", "CRITICAL_")
            } returns emptyList()

            store.findActiveCriticalsByMonth("2026-03") shouldBe emptyList()
        }
    }
}
