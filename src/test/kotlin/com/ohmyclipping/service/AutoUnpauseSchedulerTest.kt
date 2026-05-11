package com.ohmyclipping.service

import com.ohmyclipping.model.Category
import com.ohmyclipping.model.CategoryStatus
import com.ohmyclipping.observability.ClippingMetrics
import com.ohmyclipping.store.CategoryStore
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.time.Duration
import java.time.Instant

class AutoUnpauseSchedulerTest {

    private val categoryStore = mockk<CategoryStore>(relaxed = true)
    private val metrics = mockk<ClippingMetrics>(relaxed = true) {
        every { recordSchedulerRun(any<String>(), any<() -> Any?>()) } answers {
            secondArg<() -> Any?>().invoke()
        }
    }
    private val scheduler = AutoUnpauseScheduler(categoryStore, metrics)

    private val baseCategory = Category(
        id = "cat-1",
        name = "테스트 카테고리",
        status = CategoryStatus.PAUSED,
        pausedAt = Instant.now().minus(Duration.ofDays(35))
    )

    @Nested
    inner class `자동 해제` {

        @Test
        fun `30일 초과 일시정지 카테고리를 자동 해제한다`() {
            // Given: 35일 전에 일시정지된 카테고리
            every { categoryStore.findExpiredPaused(any()) } returns listOf(baseCategory)
            every { categoryStore.resume(baseCategory.id) } returns true

            // When
            scheduler.resumeExpiredPauses()

            // Then: resume이 정확히 1회 호출된다
            verify(exactly = 1) { categoryStore.resume(baseCategory.id) }
        }

        @Test
        fun `만료된 카테고리가 없으면 아무것도 하지 않는다`() {
            // Given: 만료 카테고리 없음
            every { categoryStore.findExpiredPaused(any()) } returns emptyList()

            // When
            scheduler.resumeExpiredPauses()

            // Then: resume이 호출되지 않는다
            verify(exactly = 0) { categoryStore.resume(any()) }
        }

        @Test
        fun `하나가 실패해도 나머지는 계속 처리한다`() {
            // Given: 두 카테고리 중 첫 번째 처리 시 예외 발생
            val cat1 = baseCategory.copy(id = "cat-1", pausedAt = Instant.now().minus(Duration.ofDays(35)))
            val cat2 = baseCategory.copy(id = "cat-2", pausedAt = Instant.now().minus(Duration.ofDays(40)))
            every { categoryStore.findExpiredPaused(any()) } returns listOf(cat1, cat2)
            every { categoryStore.resume("cat-1") } throws RuntimeException("DB error")
            every { categoryStore.resume("cat-2") } returns true

            // When
            scheduler.resumeExpiredPauses()

            // Then: 두 카테고리 모두 처리 시도된다
            verify(exactly = 1) { categoryStore.resume("cat-1") }
            verify(exactly = 1) { categoryStore.resume("cat-2") }
        }

        @Test
        fun `MAX_PAUSE_DURATION이 30일로 설정되어 있다`() {
            // 정책 상수값이 30일인지 검증한다
            assert(AutoUnpauseScheduler.MAX_PAUSE_DURATION == Duration.ofDays(30))
        }

        @Test
        fun `findExpiredPaused에 MAX_PAUSE_DURATION을 전달한다`() {
            // Given
            every { categoryStore.findExpiredPaused(AutoUnpauseScheduler.MAX_PAUSE_DURATION) } returns emptyList()

            // When
            scheduler.resumeExpiredPauses()

            // Then: 정확한 duration 값으로 조회한다
            verify(exactly = 1) { categoryStore.findExpiredPaused(AutoUnpauseScheduler.MAX_PAUSE_DURATION) }
        }
    }
}
